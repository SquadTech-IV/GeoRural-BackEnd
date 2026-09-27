package br.com.fatec.georural.service.treatment.embargo.state;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.FonteDoDadoRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class MetadataResolutionState implements EmbargoState {

    private static final String NOME_FONTE = "IBAMA/ICMBio - Embargos Ambientais";
    private static final String ORGAO_FONTE = "IBAMA / ICMBio";
    private static final String NOME_CONJUNTO = "Embargos Ambientais Federais";
    private static final String TIPO_CAMADA = "EMBARGO";

    // Caminho fixo do shapefile com o limite do estado do Paraná (recorte
    // do shapefile de limites estaduais do IBGE, filtrado só pro PR).
    // Coloque o arquivo (.shp + .shx + .dbf + .prj) nesse local uma única vez.
    private static final String LIMITE_PARANA_PATH = "src/main/resources/shapefiles/limite_estadual_pr.shp";

    @Override
    public void execute(Embargo context) {
        System.out.println("[0/8] Resolvendo metadados do arquivo de embargos...");

        FonteDoDadoRepository fonteRepository = SpringContextHolder.getBean(FonteDoDadoRepository.class);
        ConjuntoDadosRepository conjuntoRepository = SpringContextHolder.getBean(ConjuntoDadosRepository.class);
        ArquivoBrutoRepository arquivoRepository = SpringContextHolder.getBean(ArquivoBrutoRepository.class);
        VersaoRepository versaoRepository = SpringContextHolder.getBean(VersaoRepository.class);

        FonteDoDado fonte = fonteRepository.findByNome(NOME_FONTE).orElseGet(() -> {
            FonteDoDado nova = new FonteDoDado();
            nova.setNome(NOME_FONTE);
            nova.setOrgao(ORGAO_FONTE);
            nova.setStatus("S"); // CHECK (fon_status IN ('S','N'))
            nova.setDataCadastro(LocalDateTime.now());
            return fonteRepository.save(nova);
        });

        ConjuntoDados conjunto = conjuntoRepository.findByNome(NOME_CONJUNTO).orElseGet(() -> {
            ConjuntoDados novo = new ConjuntoDados();
            novo.setFonte(fonte);
            novo.setNome(NOME_CONJUNTO);
            novo.setTipoCamada(TIPO_CAMADA); // CHECK: precisa ser um dos valores do enum
            novo.setSituacao("RASCUNHO"); // precisa ser setado explicitamente — Hibernate valida
                                           // nullable=false ANTES do DEFAULT do banco entrar em ação
            novo.setDataCadastro(LocalDateTime.now());
            return conjuntoRepository.save(novo);
        });

        String hash = calcularHash(context.getEmbargoFile());

        // arq_hash é UNIQUE — reaproveita se o mesmo arquivo já foi processado antes
        ArquivoBruto arquivoBruto = arquivoRepository.findByHash(hash).orElseGet(() -> {
            ArquivoBruto novo = new ArquivoBruto();
            novo.setConjunto(conjunto);
            novo.setNome(context.getEmbargoFile().getName());
            novo.setFormato("SHP");
            novo.setPathArmazenamento(context.getEmbargoFile().getAbsolutePath());
            novo.setTamanhoBytes(context.getEmbargoFile().length());
            novo.setStatus("RECEBIDO"); // CHECK: valor válido
            novo.setHash(hash);
            novo.setDataUpload(LocalDateTime.now());
            return arquivoRepository.save(novo);
        });

        long proximoNumero = versaoRepository.findTopByConjuntoOrderByNumeroDesc(conjunto)
                .map(v -> v.getNumero() + 1)
                .orElse(1L);

        Versao versao = new Versao();
        versao.setConjunto(conjunto);
        versao.setNumero(proximoNumero);
        versao.setHash(hash);
        versao.setSituacao("RASCUNHO"); // idem — não confiar no DEFAULT do banco
        versao.setDataCriacao(LocalDateTime.now());
        versao = versaoRepository.save(versao);

        context.setArquivoBruto(arquivoBruto);
        context.setVersao(versao);

        // --- Área de corte: carregada de um caminho fixo dentro do projeto
        //     (NÃO derivada do banco, já que MUNICIPIO.mun_geometria ainda
        //     está vazio, e NÃO exigida no construtor do Embargo) ---
        File limiteParanaFile = new File(LIMITE_PARANA_PATH);
        if (!limiteParanaFile.exists()) {
            throw new IllegalStateException(
                    "Shapefile de limite do Paraná não encontrado em: " + LIMITE_PARANA_PATH
                            + " — coloque o arquivo (.shp/.shx/.dbf/.prj) nesse caminho antes de processar embargos.");
        }

        Geometry areaDeCorte = carregarGeometriaUnica(limiteParanaFile);
        context.setAreaDeCorte(areaDeCorte);

        try {
            FileDataStore store = FileDataStoreFinder.getDataStore(limiteParanaFile);
            context.setAreaDeCorteCRS(store.getSchema().getCoordinateReferenceSystem());
            store.dispose();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler o CRS do shapefile de limite do Paraná: " + e.getMessage(), e);
        }

        context.setState(new ReprojectionState());
    }

    private Geometry carregarGeometriaUnica(File file) {
        try {
            FileDataStore store = FileDataStoreFinder.getDataStore(file);
            SimpleFeatureCollection collection = store.getFeatureSource().getFeatures();

            Geometry uniao = null;
            try (SimpleFeatureIterator iterator = collection.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    Geometry geom = (Geometry) feature.getDefaultGeometry();
                    uniao = (uniao == null) ? geom : uniao.union(geom);
                }
            }
            store.dispose();

            if (uniao == null) {
                throw new IllegalStateException("Shapefile de limite do Paraná não contém nenhuma geometria.");
            }
            return uniao;

        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler shapefile de limite do Paraná: " + e.getMessage(), e);
        }
    }

    private String calcularHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file.toPath());
                 DigestInputStream dis = new DigestInputStream(is, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // apenas consome o stream
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Erro ao calcular hash do arquivo: " + e.getMessage(), e);
        }
    }
}