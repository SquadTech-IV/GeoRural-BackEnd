package br.com.fatec.georural.service.treatment.embargo.state;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;

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
import br.com.fatec.georural.service.treatment.audit.QuarentenaService;
import br.com.fatec.georural.service.treatment.common.LimiteParanaProvider;
import br.com.fatec.georural.service.treatment.common.LimiteParanaProvider.LimiteParana;
import br.com.fatec.georural.service.treatment.common.state.ReprojectionState;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.embargo.EmbargoTreatment;

public class MetadataResolutionState implements TreatmentState {

    private static final String NOME_FONTE = "IBAMA/ICMBio - Embargos Ambientais";
    private static final String NOME_CONJUNTO = "Embargos Ambientais Federais";
    private static final String TIPO_CAMADA = "EMBARGO";

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
                    novo.setTipoConjunto(tipo);
                    novo.setSituacao("RASCUNHO"); // Hibernate valida nullable=false ANTES do DEFAULT do banco
                    novo.setDataCadastro(LocalDateTime.now());

                    return conjuntoRepository.save(novo);
                });      

        String hash = calcularHash(context.getEmbargoFile());

        System.out.println("HASH CALCULADO: " + hash);

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
                    novo.setStatus("RECEBIDO");
                    novo.setHash(hash);
                    novo.setDataUpload(LocalDateTime.now());

                    return arquivoRepository.save(novo);
                });

        long proximoNumero = versaoRepository
                .findTopByConjuntoOrderByNumeroDesc(conjunto)
                .map(v -> v.getNumero() + 1)
                .orElse(1L);

        ExecucaoPipeline execucao = SpringContextHolder
                .getBean(ExecucaoPipelineService.class)
                .iniciar(conjunto, "TRATAMENTO");

        context.setExecucaoPipeline(execucao);

        Versao versao = new Versao();
        versao.setConjunto(conjunto);
        versao.setNumero(proximoNumero);
        versao.setHash(hash);
        versao.setSituacao("RASCUNHO");
        versao.setDataCriacao(LocalDateTime.now());
        versao = versaoRepository.save(versao);

        context.setArquivoBruto(arquivoBruto);
        context.setVersao(versao);

        // Validação desta execução: a ela ficam ligados os registros da QUARENTENA
        context.setValidacao(
                SpringContextHolder
                        .getBean(QuarentenaService.class)
                        .iniciarValidacao(arquivoBruto));

        // Limite do Paraná: agora vem do provider compartilhado com o fluxo de imóvel
        LimiteParana limite = SpringContextHolder
                .getBean(LimiteParanaProvider.class)
                .carregar();

        context.setAreaDeCorte(limite.geometria());
        context.setAreaDeCorteCRS(limite.crs());

        List<SimpleFeature> features = carregarFeatures(context.getEmbargoFile());

        context.setFeatures(features);

        context.registrarTransformacao(
                "CARGA",
                features.size(),
                features.size(),
                "arquivo=" + context.getEmbargoFile().getName());

        context.setState(new ReprojectionState());
    }

    private List<SimpleFeature> carregarFeatures(File shapefile) {
        FileDataStore store = null;

        try {
            store = FileDataStoreFinder.getDataStore(shapefile);

            if (store instanceof ShapefileDataStore shp) {
                shp.setCharset(StandardCharsets.UTF_8);
            }

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
            throw new RuntimeException(
                    "Erro ao calcular hash do arquivo: " + e.getMessage(),
                    e);
        }
    }
}
