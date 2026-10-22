package br.com.fatec.georural.service.treatment.property_area.state;

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

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.FonteDoDadoRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.treatment.common.state.GeometryValidationState;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

public class MetadataResolutionState implements TreatmentState {

    private static final String NOME_FONTE = "CAR - Área do Imóvel";
    private static final String ORGAO_FONTE = "Serviço Florestal Brasileiro (SFB)";
    private static final String NOME_CONJUNTO = "Área do Imóvel";
    private static final String TIPO_CAMADA = "IMOVEL";

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea = (PropertyAreaTreatment) baseContext;

        FonteDoDadoRepository fonteRepository = SpringContextHolder.getBean(FonteDoDadoRepository.class);

        ConjuntoDadosRepository conjuntoRepository = SpringContextHolder.getBean(ConjuntoDadosRepository.class);

        ArquivoBrutoRepository arquivoRepository = SpringContextHolder.getBean(ArquivoBrutoRepository.class);

        VersaoRepository versaoRepository = SpringContextHolder.getBean(VersaoRepository.class);

        FonteDoDado fonte = fonteRepository.findByNome(NOME_FONTE)
                .orElseGet(() -> {

                    FonteDoDado nova = new FonteDoDado();

                    nova.setNome(NOME_FONTE);
                    nova.setStatus("S");
                    nova.setDataCadastro(
                            LocalDateTime.now());

                    return fonteRepository.save(nova);
                });

        ConjuntoDados conjunto = conjuntoRepository
                .findByNome(NOME_CONJUNTO)
                .orElseGet(() -> {

                    ConjuntoDados novo = new ConjuntoDados();

                    novo.setFonte(fonte);
                    novo.setNome(NOME_CONJUNTO);
                    novo.setSituacao("RASCUNHO");
                    novo.setDataCadastro(
                            LocalDateTime.now());

                    return conjuntoRepository.save(novo);
                });

        String hash = calcularHash(
                propertyArea.getPropertyAreaFile());

        ArquivoBruto arquivoBruto = new ArquivoBruto();

        arquivoBruto.setConjunto(conjunto);
        arquivoBruto.setNome(
                propertyArea
                        .getPropertyAreaFile()
                        .getName());

        arquivoBruto.setFormato("SHP");

        arquivoBruto.setPathArmazenamento(
                propertyArea
                        .getPropertyAreaFile()
                        .getAbsolutePath());

        arquivoBruto.setTamanhoBytes(
                propertyArea
                        .getPropertyAreaFile()
                        .length());

        arquivoBruto.setStatus("RECEBIDO");
        arquivoBruto.setHash(hash);
        arquivoBruto.setDataUpload(
                LocalDateTime.now());

        arquivoBruto = arquivoRepository.save(
                arquivoBruto);

        long proximoNumero = versaoRepository
                .findTopByConjuntoOrderByNumeroDesc(
                        conjunto)
                .map(v -> v.getNumero() + 1)
                .orElse(1L);

        Versao versao = new Versao();

        versao.setConjunto(conjunto);
        versao.setNumero(proximoNumero);
        versao.setHash(hash);
        versao.setSituacao("RASCUNHO");
        versao.setDataCriacao(
                LocalDateTime.now());

        versao = versaoRepository.save(
                versao);

        propertyArea.setArquivoBruto(
                arquivoBruto);

        propertyArea.setVersao(
                versao);

        propertyArea.setFeatures(
                carregarFeatures(
                        propertyArea
                                .getPropertyAreaFile()));

        propertyArea.setState(
                new GeometryValidationState());
    }

    private String calcularHash(File file) {

        try {

            MessageDigest digest = MessageDigest.getInstance(
                    "SHA-256");

            try (InputStream is = Files.newInputStream(
                    file.toPath());

                    DigestInputStream dis = new DigestInputStream(
                            is,
                            digest)) {

                byte[] buffer = new byte[8192];

                while (dis.read(buffer) != -1) {
                }
            }

            StringBuilder sb = new StringBuilder();

            for (byte b : digest.digest()) {

                sb.append(
                        String.format(
                                "%02x",
                                b));
            }

            return sb.toString();

        } catch (NoSuchAlgorithmException | IOException e) {

            throw new RuntimeException(
                    "Erro ao calcular hash do arquivo: "
                            + e.getMessage(),
                    e);
        }
    }

    private List<SimpleFeature> carregarFeatures(File shapefile) {

        ShapefileDataStore dataStore = null;

        try {

            dataStore = new ShapefileDataStore(
                    shapefile.toURI().toURL());

            SimpleFeatureCollection collection = dataStore
                    .getFeatureSource()
                    .getFeatures();

            List<SimpleFeature> features = new ArrayList<>();

            try (SimpleFeatureIterator iterator = collection.features()) {

                while (iterator.hasNext()) {

                    features.add(
                            iterator.next());
                }
            }

            return features;

        } catch (IOException e) {

            throw new RuntimeException(
                    "Erro ao ler shapefile: "
                            + e.getMessage(),
                    e);

        } finally {

            if (dataStore != null) {
                dataStore.dispose();
            }
        }
    }
}