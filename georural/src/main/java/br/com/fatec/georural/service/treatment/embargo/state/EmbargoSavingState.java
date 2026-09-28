package br.com.fatec.georural.service.treatment.embargo.state;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.repository.EmbargoRepository;
import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class EmbargoSavingState implements EmbargoState {

    private static final int SRID_SIRGAS_2000 = 4674;
    private static final int BATCH_SIZE = 1000; // Grava a cada 1.000 registros

    @Override
    public void execute(Embargo context) {
        System.out.println("[8/8] Salvando registros na tabela EMBARGO (Oracle Spatial em Batch)...");

        EmbargoRepository embargoRepository = SpringContextHolder.getBean(EmbargoRepository.class);
        EntityManager entityManager = SpringContextHolder.getBean(EntityManager.class);
        PlatformTransactionManager transactionManager = SpringContextHolder.getBean(PlatformTransactionManager.class);

        SimpleFeatureCollection collection = context.getCurrentFeatures();

        if (collection == null || collection.isEmpty()) {
            System.out.println("Nenhum registro para salvar na tabela EMBARGO.");
            context.setState(null);
            return;
        }

        int totalFeatures = collection.size();
        int processados = 0;
        int salvos = 0;

        List<br.com.fatec.georural.entity.Embargo> batchList = new ArrayList<>();
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());

        try (SimpleFeatureIterator iterator = collection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Geometry geom = (Geometry) feature.getDefaultGeometry();

                if (geom != null) {
                    geom.setSRID(SRID_SIRGAS_2000);

                    br.com.fatec.georural.entity.Embargo embargoEntity = new br.com.fatec.georural.entity.Embargo();
                    embargoEntity.setGeometria(geom);
                    embargoEntity.setArquivoBruto(context.getArquivoBruto());
                    embargoEntity.setVersao(context.getVersao());

                    // Mapeia o identificador do embargo (ex: NUM_TAD / TAD)
                    Object idAtributo = feature.getAttribute("num_tad");
                    if (idAtributo == null) {
                        idAtributo = feature.getAttribute("NUM_TAD");
                    }
                    embargoEntity.setIdentificador(idAtributo != null ? idAtributo.toString() : feature.getID());

                    batchList.add(embargoEntity);
                    salvos++;
                }

                processados++;

                // Executa a persistência em Lote (Batch)
                if (batchList.size() >= BATCH_SIZE) {
                    embargoRepository.saveAll(batchList);
                    entityManager.flush();
                    entityManager.clear();
                    batchList.clear();

                    System.out.println(String.format("Progresso da gravação: %d / %d registros inseridos...", processados, totalFeatures));
                }
            }

            // Grava os registros remanescentes do último lote
            if (!batchList.isEmpty()) {
                embargoRepository.saveAll(batchList);
                entityManager.flush();
                entityManager.clear();
                batchList.clear();
            }

            transactionManager.commit(status);
            System.out.println("Gravação concluída com sucesso! Total de registros inseridos no Oracle: " + salvos);

        } catch (Exception e) {
            if (!status.isCompleted()) {
                transactionManager.rollback(status);
            }
            throw new RuntimeException("Erro ao gravar dados no Oracle Spatial: " + e.getMessage(), e);
        }

        context.setState(null);
    }
}