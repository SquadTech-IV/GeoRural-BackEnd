package br.com.fatec.georural.service.treatment.embargo.state;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.FonteDoDadoRepository;
import br.com.fatec.georural.repository.TipoConjuntoRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.treatment.audit.ExecucaoPipelineService;
import br.com.fatec.georural.service.treatment.common.state.ReprojectionState;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.embargo.EmbargoTreatment;

public class MetadataResolutionState implements TreatmentState {

    private static final String NOME_FONTE = "IBAMA/ICMBio - Embargos Ambientais";
    private static final String ORGAO_FONTE = "IBAMA / ICMBio";
    private static final String NOME_CONJUNTO = "Embargos Ambientais Federais";
    private static final String TIPO_CAMADA = "EMBARGO";

    private static final String LIMITE_PARANA_PATH = "src/main/resources/shapefiles/limite_estadual_pr.shp";

    @Override
    public void execute(BaseTreatment baseContext) {
        EmbargoTreatment context = (EmbargoTreatment) baseContext;

        System.out.println(
                "[0/8] Resolvendo metadados do arquivo de embargos...");

        FonteDoDadoRepository fonteRepository = SpringContextHolder.getBean(FonteDoDadoRepository.class);

        ConjuntoDadosRepository conjuntoRepository = SpringContextHolder.getBean(ConjuntoDadosRepository.class);

        ArquivoBrutoRepository arquivoRepository = SpringContextHolder.getBean(ArquivoBrutoRepository.class);

        VersaoRepository versaoRepository = SpringContextHolder.getBean(VersaoRepository.class);

        TipoConjuntoRepository tipos = SpringContextHolder.getBean(TipoConjuntoRepository.class);

        var tipo = tipos.findByCodigoIgnoreCase(TIPO_CAMADA)
                .orElseThrow(() -> new IllegalStateException(
                        "Tipo não cadastrado em TIPO_CONJUNTO: " + TIPO_CAMADA));

        FonteDoDado fonte = fonteRepository.findByNome(NOME_FONTE)
                .orElseGet(() -> {
                    FonteDoDado nova = new FonteDoDado();
                    nova.setNome(NOME_FONTE);
                    nova.setSigla("IBAMA/ICMBio");
                    nova.setStatus("S"); // CHECK (fon_status IN ('S','N'))
                    nova.setDataCadastro(LocalDateTime.now());

                    return fonteRepository.save(nova);
                });

        ConjuntoDados conjunto = conjuntoRepository
                .findByFonteIdAndTipoConjuntoId(
                        fonte.getId(),
                        tipo.getId())
                .orElseGet(() -> {
                    ConjuntoDados novo = new ConjuntoDados();
                    novo.setFonte(fonte);
                    novo.setNome(NOME_CONJUNTO);
                    novo.setTipoConjunto(tipo); // relacionamento com TIPO_CONJUNTO
                    novo.setSituacao("RASCUNHO"); // precisa ser setado explicitamente — Hibernate valida
                                                  // nullable=false ANTES do DEFAULT do banco entrar em ação
                    novo.setDataCadastro(LocalDateTime.now());

                    return conjuntoRepository.save(novo);
                });

        String hash = calcularHash(context.getEmbargoFile());

        // arq_hash é UNIQUE — reaproveita se o mesmo arquivo já foi processado antes
        ArquivoBruto arquivoBruto = arquivoRepository.findByHash(hash)
                .orElseGet(() -> {
                    ArquivoBruto novo = new ArquivoBruto();
                    novo.setConjunto(conjunto);
                    novo.setNome(context.getEmbargoFile().getName());
                    novo.setFormato("SHP");
                    novo.setPathArmazenamento(
                            context.getEmbargoFile().getAbsolutePath());
                    novo.setTamanhoBytes(context.getEmbargoFile().length());
                    novo.setStatus("RECEBIDO"); // CHECK: valor válido
                    novo.setHash(hash);
                    novo.setDataUpload(LocalDateTime.now());

                    return arquivoRepository.save(novo);
                });

        long proximoNumero = versaoRepository
                .findTopByConjuntoOrderByNumeroDesc(conjunto)
                .map(v -> v.getNumero() + 1)
                .orElse(1L);

        Versao versao = new Versao();
        ExecucaoPipeline execucao = SpringContextHolder
        .getBean(
                ExecucaoPipelineService.class)
        .iniciar(
                conjunto,
                "TRATAMENTO");

        context.setExecucaoPipeline(
                execucao);
        versao.setConjunto(conjunto);
        versao.setNumero(proximoNumero);
        versao.setHash(hash);
        versao.setSituacao("RASCUNHO"); // idem — não confiar no DEFAULT do banco
        versao.setDataCriacao(LocalDateTime.now());
        versao = versaoRepository.save(versao);

        context.setArquivoBruto(arquivoBruto);
        context.setVersao(versao);

        File limiteParanaFile = new File(LIMITE_PARANA_PATH);

        if (!limiteParanaFile.exists()) {
            throw new IllegalStateException(
                    "Shapefile de limite do Paraná não encontrado em: "
                            + LIMITE_PARANA_PATH);
        }

        Geometry areaDeCorte = carregarGeometriaUnica(limiteParanaFile);
        context.setAreaDeCorte(areaDeCorte);

        FileDataStore limiteStore = null;

        try {
            limiteStore = abrirShapefile(limiteParanaFile);

            context.setAreaDeCorteCRS(
                    limiteStore.getSchema().getCoordinateReferenceSystem());
        } catch (IOException e) {
            throw new RuntimeException(
                    "Erro ao ler o CRS do shapefile de limite do Paraná: "
                            + e.getMessage(),
                    e);
        } finally {
            if (limiteStore != null) {
                limiteStore.dispose();
            }
        }

        context.setFeatures(
                carregarFeatures(context.getEmbargoFile()));

        context.setState(new ReprojectionState());
    }

    private FileDataStore abrirShapefile(File file) throws IOException {
        FileDataStore store = FileDataStoreFinder.getDataStore(file);

        if (store == null) {
            throw new IOException(
                    "Não foi possível abrir o shapefile: "
                            + file.getAbsolutePath());
        }

        return store;
    }

    private List<SimpleFeature> carregarFeatures(File shapefile) {
        FileDataStore store = null;

        try {
            store = abrirShapefile(shapefile);

            SimpleFeatureCollection collection = store.getFeatureSource().getFeatures();

            List<SimpleFeature> features = new ArrayList<>();

            try (SimpleFeatureIterator iterator = collection.features()) {
                while (iterator.hasNext()) {
                    features.add(iterator.next());
                }
            }

            return features;
        } catch (IOException e) {
            throw new RuntimeException(
                    "Erro ao carregar feições do embargo: " + e.getMessage(),
                    e);
        } finally {
            if (store != null) {
                store.dispose();
            }
        }
    }

    private Geometry carregarGeometriaUnica(File file) {
        FileDataStore store = null;

        try {
            store = abrirShapefile(file);

            SimpleFeatureCollection collection = store.getFeatureSource().getFeatures();

            Geometry uniao = null;

            try (SimpleFeatureIterator iterator = collection.features()) {
                while (iterator.hasNext()) {
                    Geometry geom = (Geometry) iterator.next().getDefaultGeometry();

                    if (geom != null) {
                        uniao = uniao == null
                                ? geom
                                : uniao.union(geom);
                    }
                }
            }

            if (uniao == null || uniao.isEmpty()) {
                throw new IllegalStateException(
                        "Shapefile de limite do Paraná não contém nenhuma geometria válida para o recorte.");
            }

            return uniao;
        } catch (IOException e) {
            throw new RuntimeException(
                    "Erro ao ler shapefile de limite do Paraná: "
                            + e.getMessage(),
                    e);
        } finally {
            if (store != null) {
                store.dispose();
            }
        }
    }

    private String calcularHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (
                    InputStream is = Files.newInputStream(file.toPath());
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
            throw new RuntimeException(
                    "Erro ao calcular hash do arquivo: " + e.getMessage(),
                    e);
        }
    }
}