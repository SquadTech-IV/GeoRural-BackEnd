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
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.FonteDoDadoRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.treatment.common.state.ReprojectionState;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.embargo.EmbargoTreatment;

public class MetadataResolutionState
        implements TreatmentState {

    private static final String NOME_FONTE = "IBAMA/ICMBio - Embargos Ambientais";

    private static final String ORGAO_FONTE = "IBAMA / ICMBio";

    private static final String NOME_CONJUNTO = "Embargos Ambientais Federais";

    private static final String LIMITE_PARANA_PATH = "src/main/resources/shapefiles/limite_estadual_pr.shp";

    @Override
    public void execute(BaseTreatment baseContext) {

        EmbargoTreatment context = (EmbargoTreatment) baseContext;

        System.out.println(
                "[0/8] Resolvendo metadados do arquivo de embargos...");

        FonteDoDadoRepository fonteRepository = SpringContextHolder.getBean(
                FonteDoDadoRepository.class);

        ConjuntoDadosRepository conjuntoRepository = SpringContextHolder.getBean(
                ConjuntoDadosRepository.class);

        ArquivoBrutoRepository arquivoRepository = SpringContextHolder.getBean(
                ArquivoBrutoRepository.class);

        VersaoRepository versaoRepository = SpringContextHolder.getBean(
                VersaoRepository.class);

        FonteDoDado fonte = fonteRepository.findByNome(NOME_FONTE)
                .orElseGet(() -> {

                    FonteDoDado nova = new FonteDoDado();

                    nova.setNome(
                            NOME_FONTE);

                    nova.setStatus("S");

                    nova.setDataCadastro(
                            LocalDateTime.now());

                    return fonteRepository.save(
                            nova);
                });

        ConjuntoDados conjunto = conjuntoRepository.findByNome(
                NOME_CONJUNTO)
                .orElseGet(() -> {

                    ConjuntoDados novo = new ConjuntoDados();

                    novo.setFonte(fonte);

                    novo.setNome(
                            NOME_CONJUNTO);

                    novo.setSituacao(
                            "RASCUNHO");

                    novo.setDataCadastro(
                            LocalDateTime.now());

                    return conjuntoRepository.save(
                            novo);
                });

        String hash = calcularHash(
                context.getEmbargoFile());

        ArquivoBruto arquivoBruto = arquivoRepository
                .findByHash(hash)
                .orElseGet(() -> {

                    ArquivoBruto novo = new ArquivoBruto();

                    novo.setConjunto(
                            conjunto);

                    novo.setNome(
                            context.getEmbargoFile()
                                    .getName());

                    novo.setFormato(
                            "SHP");

                    novo.setPathArmazenamento(
                            context.getEmbargoFile()
                                    .getAbsolutePath());

                    novo.setTamanhoBytes(
                            context.getEmbargoFile()
                                    .length());

                    novo.setStatus(
                            "RECEBIDO");

                    novo.setHash(
                            hash);

                    novo.setDataUpload(
                            LocalDateTime.now());

                    return arquivoRepository.save(
                            novo);
                });

        long proximoNumero = versaoRepository
                .findTopByConjuntoOrderByNumeroDesc(
                        conjunto)
                .map(v -> v.getNumero() + 1)
                .orElse(1L);

        Versao versao = new Versao();

        versao.setConjunto(
                conjunto);

        versao.setNumero(
                proximoNumero);

        versao.setHash(
                hash);

        versao.setSituacao(
                "RASCUNHO");

        versao.setDataCriacao(
                LocalDateTime.now());

        versao = versaoRepository.save(
                versao);

        context.setArquivoBruto(
                arquivoBruto);

        context.setVersao(
                versao);

        File limiteParanaFile = new File(
                LIMITE_PARANA_PATH);

        if (!limiteParanaFile.exists()) {

            throw new IllegalStateException(
                    "Shapefile de limite do Paraná não encontrado em: "
                            + LIMITE_PARANA_PATH);
        }

        Geometry areaDeCorte = carregarGeometriaUnica(
                limiteParanaFile);

        context.setAreaDeCorte(
                areaDeCorte);

        try {

            FileDataStore store = FileDataStoreFinder
                    .getDataStore(
                            limiteParanaFile);

            context.setAreaDeCorteCRS(
                    store.getSchema()
                            .getCoordinateReferenceSystem());

            store.dispose();

        } catch (IOException e) {

            throw new RuntimeException(
                    "Erro ao ler CRS do Paraná",
                    e);
        }

        context.setFeatures(
                carregarFeatures(
                        context.getEmbargoFile()));

        context.setState(
                new ReprojectionState());
    }

    private List<SimpleFeature> carregarFeatures(
            File shapefile) {

        try {

            FileDataStore store = FileDataStoreFinder.getDataStore(
                    shapefile);

            SimpleFeatureCollection collection = store.getFeatureSource()
                    .getFeatures();

            List<SimpleFeature> features = new ArrayList<>();

            try (SimpleFeatureIterator iterator = collection.features()) {

                while (iterator.hasNext()) {

                    features.add(
                            iterator.next());
                }
            }

            store.dispose();

            return features;

        } catch (Exception e) {

            throw new RuntimeException(
                    "Erro ao carregar feições do embargo",
                    e);
        }
    }

    private Geometry carregarGeometriaUnica(
            File file) {

        try {

            FileDataStore store = FileDataStoreFinder
                    .getDataStore(file);

            SimpleFeatureCollection collection = store.getFeatureSource()
                    .getFeatures();

            Geometry uniao = null;

            try (SimpleFeatureIterator iterator = collection.features()) {

                while (iterator.hasNext()) {

                    Geometry geom = (Geometry) iterator
                            .next()
                            .getDefaultGeometry();

                    uniao = (uniao == null)
                            ? geom
                            : uniao.union(
                                    geom);
                }
            }

            store.dispose();

            return uniao;

        } catch (IOException e) {

            throw new RuntimeException(
                    "Erro ao ler limite do Paraná",
                    e);
        }
    }

    private String calcularHash(
            File file) {

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
                    "Erro ao calcular hash",
                    e);
        }
    }
}