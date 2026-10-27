package br.com.fatec.georural.service.treatment.reserva_legal.state;

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
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.treatment.audit.ExecucaoPipelineService;
import br.com.fatec.georural.service.treatment.audit.QuarentenaService;
import br.com.fatec.georural.service.treatment.common.state.GeometryValidationState;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.reserva_legal.ReservaLegalTreatment;

/**
 * Primeiro estado do tratamento de Reserva Legal. Diferente do fluxo antigo de embargo/imóvel, NÃO cria
 * fonte/conjunto/arquivo paralelos: reusa o contexto que o orquestrador já preparou (o arquivo bruto real
 * gravado na zona bruta, o conjunto dele e a execução em andamento). Carrega as feições e cria a versão.
 */
public class MetadataResolutionState implements TreatmentState {

    @Override
    public void execute(BaseTreatment baseContext) {
        ReservaLegalTreatment ctx = (ReservaLegalTreatment) baseContext;

        VersaoRepository versaoRepository = SpringContextHolder.getBean(VersaoRepository.class);

        File arquivo = ctx.getReservaLegalFile();

        ArquivoBruto arquivoBruto = ctx.getArquivoBruto();
        if (arquivoBruto == null || arquivoBruto.getConjunto() == null) {
            throw new IllegalStateException(
                    "Contexto de ingestão ausente: arquivo bruto/conjunto não resolvidos para a Reserva Legal.");
        }
        ConjuntoDados conjunto = arquivoBruto.getConjunto();

        ExecucaoPipeline execucao = ctx.getExecucaoPipeline();
        if (execucao == null) {
            execucao = SpringContextHolder.getBean(ExecucaoPipelineService.class)
                    .iniciar(conjunto, arquivoBruto, "TRATAMENTO");
            ctx.setExecucaoPipeline(execucao);
        }

        long proximoNumero = versaoRepository
                .findTopByConjuntoOrderByNumeroDesc(conjunto)
                .map(v -> v.getNumero() + 1)
                .orElse(1L);

        Versao versao = new Versao();
        versao.setConjunto(conjunto);
        versao.setNumero(proximoNumero);
        versao.setHash(calcularHash(arquivo));
        versao.setSituacao("RASCUNHO");
        versao.setDataCriacao(LocalDateTime.now());
        versao = versaoRepository.save(versao);

        ctx.setVersao(versao);

        // Validação desta execução: a ela ficam ligados os registros da QUARENTENA
        ctx.setValidacao(
                SpringContextHolder.getBean(QuarentenaService.class)
                        .iniciarValidacao(arquivoBruto, execucao));

        List<SimpleFeature> features = carregarFeatures(arquivo);
        ctx.setFeatures(features);
        ctx.setTotalCarregado(features.size());

        ctx.registrarTransformacao(
                "CARGA", features.size(), features.size(), "arquivo=" + arquivo.getName());

        ctx.setState(new GeometryValidationState());
    }

    private List<SimpleFeature> carregarFeatures(File shapefile) {
        FileDataStore store = null;
        try {
            store = FileDataStoreFinder.getDataStore(shapefile);
            if (store == null) {
                throw new IllegalArgumentException(
                        "Não foi possível abrir o shapefile de Reserva Legal: " + shapefile.getName());
            }
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
                    "Erro ao carregar feições da Reserva Legal: " + e.getMessage(), e);
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
                    "Erro ao calcular hash do arquivo: " + e.getMessage(), e);
        }
    }
}
