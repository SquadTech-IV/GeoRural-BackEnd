package br.com.fatec.georural.service.treatment.property_area.state;

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
import br.com.fatec.georural.service.treatment.common.state.GeometryValidationState;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

public class MetadataResolutionState implements TreatmentState {

    private static final String NOME_FONTE = "CAR - Área do Imóvel";
    private static final String NOME_CONJUNTO = "Área do Imóvel";
    private static final String TIPO_CAMADA = "IMOVEL"; // código cadastrado em TIPO_CONJUNTO

    @Override
    public void execute(BaseTreatment baseContext) {
        PropertyAreaTreatment propertyArea = (PropertyAreaTreatment) baseContext;

        VersaoRepository versaoRepository = SpringContextHolder.getBean(VersaoRepository.class);

        File arquivo = propertyArea.getPropertyAreaFile();

        // Contexto já preparado pelo orquestrador: o arquivo bruto REAL (gravado na zona bruta/OCI),
        // o conjunto dele e a execução já iniciada. O tratamento CONSOME esse contexto, em vez de criar
        // fonte/conjunto/arquivo bruto paralelos apontando para o arquivo temporário extraído (o que gerava
        // um ArquivoBruto fantasma com caminho local e desvinculava o upload real do resultado).
        ArquivoBruto arquivoBruto = propertyArea.getArquivoBruto();
        ExecucaoPipeline execucao = propertyArea.getExecucaoPipeline();
        ConjuntoDados conjunto;

        if (arquivoBruto != null && arquivoBruto.getConjunto() != null) {
            conjunto = arquivoBruto.getConjunto();
            if (execucao == null) {
                execucao = SpringContextHolder.getBean(ExecucaoPipelineService.class)
                        .iniciar(conjunto, arquivoBruto, "TRATAMENTO");
                propertyArea.setExecucaoPipeline(execucao);
            }
        } else {
            // Fluxo legado/standalone (sem orquestrador): resolve fonte/conjunto/arquivo por conta própria.
            FonteDoDadoRepository fonteRepository = SpringContextHolder.getBean(FonteDoDadoRepository.class);
            ConjuntoDadosRepository conjuntoRepository = SpringContextHolder.getBean(ConjuntoDadosRepository.class);
            ArquivoBrutoRepository arquivoRepository = SpringContextHolder.getBean(ArquivoBrutoRepository.class);
            TipoConjuntoRepository tipos = SpringContextHolder.getBean(TipoConjuntoRepository.class);

            var tipo = tipos.findByCodigoIgnoreCase(TIPO_CAMADA)
                    .orElseThrow(() -> new IllegalStateException(
                            "Tipo não cadastrado em TIPO_CONJUNTO: " + TIPO_CAMADA));

            FonteDoDado fonte = fonteRepository.findByNome(NOME_FONTE)
                    .orElseGet(() -> {
                        FonteDoDado nova = new FonteDoDado();
                        nova.setNome(NOME_FONTE);
                        nova.setSigla("CAR");
                        nova.setStatus("S"); // CHECK (fon_status IN ('S','N'))
                        nova.setDataCadastro(LocalDateTime.now());
                        return fonteRepository.save(nova);
                    });

            ConjuntoDados conjuntoResolvido = conjuntoRepository
                    .findByFonteIdAndTipoConjuntoId(fonte.getId(), tipo.getId())
                    .orElseGet(() -> {
                        ConjuntoDados novo = new ConjuntoDados();
                        novo.setFonte(fonte);
                        novo.setNome(NOME_CONJUNTO);
                        novo.setTipoConjunto(tipo);
                        novo.setSituacao("RASCUNHO"); // CHECK (con_situacao IN
                                                      // ('RASCUNHO','EM_VALIDACAO','PUBLICADO','ARQUIVADO'))
                        novo.setDataCadastro(LocalDateTime.now());
                        return conjuntoRepository.save(novo);
                    });
            conjunto = conjuntoResolvido;

            String hashLegado = calcularHash(arquivo);
            arquivoBruto = arquivoRepository.findByHash(hashLegado)
                    .orElseGet(() -> {
                        ArquivoBruto novo = new ArquivoBruto();
                        novo.setConjunto(conjuntoResolvido);
                        novo.setNome(arquivo.getName());
                        novo.setFormato("SHP");
                        novo.setPathArmazenamento(arquivo.getAbsolutePath());
                        novo.setTamanhoBytes(arquivo.length());
                        novo.setStatus("RECEBIDO");
                        novo.setHash(hashLegado);
                        novo.setDataUpload(LocalDateTime.now());
                        return arquivoRepository.save(novo);
                    });

            execucao = SpringContextHolder.getBean(ExecucaoPipelineService.class)
                    .iniciar(conjunto, arquivoBruto, "TRATAMENTO");
            propertyArea.setExecucaoPipeline(execucao);
        }

        String hash = calcularHash(arquivo);

        long proximoNumero = versaoRepository
                .findTopByConjuntoOrderByNumeroDesc(conjunto)
                .map(v -> v.getNumero() + 1)
                .orElse(1L);

        Versao versao = new Versao();
        versao.setConjunto(conjunto);
        versao.setNumero(proximoNumero);
        versao.setHash(hash);
        versao.setSituacao("RASCUNHO");
        versao.setDataCriacao(LocalDateTime.now());

        versao = versaoRepository.save(versao);

        propertyArea.setArquivoBruto(arquivoBruto);
        propertyArea.setVersao(versao);

        // Validação desta execução: a ela ficam ligados os registros da QUARENTENA
        propertyArea.setValidacao(
                SpringContextHolder
                        .getBean(QuarentenaService.class)
                        .iniciarValidacao(arquivoBruto, execucao));

        // US09.01: o imóvel também é recortado pelo limite do Paraná
        LimiteParana limite = SpringContextHolder
                .getBean(LimiteParanaProvider.class)
                .carregar();

        propertyArea.setAreaDeCorte(limite.geometria());
        propertyArea.setAreaDeCorteCRS(limite.crs());

        List<SimpleFeature> features = carregarFeatures(arquivo);

        propertyArea.setFeatures(features);

        propertyArea.setTotalCarregado(features.size());

        propertyArea.registrarTransformacao(
                "CARGA",
                features.size(),
                features.size(),
                "arquivo=" + arquivo.getName());

        propertyArea.setState(new GeometryValidationState());
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

    private List<SimpleFeature> carregarFeatures(File shapefile) {
        ShapefileDataStore dataStore = null;

        try {
            dataStore = new ShapefileDataStore(shapefile.toURI().toURL());
            dataStore.setCharset(StandardCharsets.UTF_8);

            SimpleFeatureCollection collection = dataStore
                    .getFeatureSource()
                    .getFeatures();

            List<SimpleFeature> features = new ArrayList<>();

            try (SimpleFeatureIterator iterator = collection.features()) {
                while (iterator.hasNext()) {
                    features.add(iterator.next());
                }
            }

            return features;
        } catch (IOException e) {
            throw new RuntimeException(
                    "Erro ao ler shapefile: " + e.getMessage(),
                    e);
        } finally {
            if (dataStore != null) {
                dataStore.dispose();
            }
        }
    }
}
