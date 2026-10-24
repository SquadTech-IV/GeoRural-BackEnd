package br.com.fatec.georural.service.treatment.embargo.state;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.repository.EmbargoRepository;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import java.time.LocalDateTime;

import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;

public class EmbargoSavingState implements TreatmentState {

        private static final int SRID_SIRGAS_2000 = 4674;
        private static final int BATCH_SIZE = 1000;

        @Override
        public void execute(BaseTreatment context) {

                EmbargoRepository embargoRepository = SpringContextHolder.getBean(
                                EmbargoRepository.class);

                EntityManager entityManager = SpringContextHolder.getBean(
                                EntityManager.class);

                PlatformTransactionManager transactionManager = SpringContextHolder.getBean(
                                PlatformTransactionManager.class);

                ExecucaoPipelineRepository execucaoRepository = SpringContextHolder.getBean(
                                ExecucaoPipelineRepository.class);

                MovimentacaoZonaRepository movimentacaoRepository = SpringContextHolder.getBean(
                                MovimentacaoZonaRepository.class);

                List<SimpleFeature> features = context.getFeatures();

                if (features == null || features.isEmpty()) {
                        context.setState(null);
                        return;
                }

                ExecucaoPipeline execucao = context.getExecucaoPipeline();

                TransactionStatus status = transactionManager.getTransaction(
                                new DefaultTransactionDefinition());

                List<br.com.fatec.georural.entity.Embargo> batchList = new ArrayList<>();

                try {

                        for (SimpleFeature feature : features) {

                                Geometry geom = (Geometry) feature.getDefaultGeometry();

                                if (geom == null) {
                                        continue;
                                }

                                geom.setSRID(SRID_SIRGAS_2000);

                                br.com.fatec.georural.entity.Embargo embargo = new br.com.fatec.georural.entity.Embargo();

                                embargo.setGeometria(geom);
                                embargo.setArquivoBruto(
                                                context.getArquivoBruto());

                                embargo.setVersao(
                                                context.getVersao());

                                Object idAtributo = feature.getAttribute("num_tad");

                                if (idAtributo == null) {
                                        idAtributo = feature.getAttribute("NUM_TAD");
                                }

                                embargo.setIdentificador(
                                                idAtributo != null
                                                                ? idAtributo.toString()
                                                                : feature.getID());

                                batchList.add(embargo);

                                if (batchList.size() >= BATCH_SIZE) {

                                        embargoRepository.saveAll(batchList);

                                        entityManager.flush();
                                        entityManager.clear();

                                        batchList.clear();
                                }
                        }

                        if (!batchList.isEmpty()) {
                                embargoRepository.saveAll(batchList);
                                entityManager.flush();
                                entityManager.clear();
                        }

                        if (execucao != null) {

                                execucao.setStatus(
                                                "CONCLUIDA");

                                execucao.setFim(
                                                LocalDateTime.now());

                                execucaoRepository.save(
                                                execucao);

                                MovimentacaoZona movimentacao = new MovimentacaoZona();

                                movimentacao.setArquivoBruto(
                                                context.getArquivoBruto());

                                movimentacao.setExecucao(
                                                execucao);

                                movimentacao.setZona(
                                                "TRATADA");

                                execucao.setStatus(
        "CONCLUIDA");

                                movimentacao.setObservacao(
                                                "Dados tratados publicados");

                                movimentacao.setData(
                                                LocalDateTime.now());

                                movimentacaoRepository.save(
                                                movimentacao);
                        }

                        transactionManager.commit(status);

                } catch (Exception e) {

                        if (execucao != null) {

                                execucao.setStatus(
        "FALHOU");

                                execucao.setFim(
                                                LocalDateTime.now());

                                execucaoRepository.save(
                                                execucao);
                        }

                        if (!status.isCompleted()) {
                                transactionManager.rollback(status);
                        }

                        throw new RuntimeException(
                                        "Erro ao salvar embargos",
                                        e);
                }

                context.setState(null);
        }
}