package br.com.fatec.georural.service.treatment.embargo.state;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.Embargo;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.EmbargoRepository;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.treatment.common.FeatureMapper;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.storage.ZonaTratadaService;
import jakarta.persistence.EntityManager;

public class EmbargoSavingState implements TreatmentState {

        private static final int SRID_SIRGAS_2000 = 4674;
        private static final int BATCH_SIZE = 1000;

        @Override
        public void execute(BaseTreatment context) {

                EmbargoRepository embargoRepository = SpringContextHolder.getBean(EmbargoRepository.class);

                EntityManager entityManager = SpringContextHolder.getBean(EntityManager.class);

                PlatformTransactionManager transactionManager = SpringContextHolder
                                .getBean(PlatformTransactionManager.class);

                ExecucaoPipelineRepository execucaoRepository = SpringContextHolder
                                .getBean(ExecucaoPipelineRepository.class);

                VersaoRepository versaoRepository = SpringContextHolder.getBean(VersaoRepository.class);

                ConjuntoDadosRepository conjuntoRepository = SpringContextHolder
                                .getBean(ConjuntoDadosRepository.class);

                List<SimpleFeature> features = context.getFeatures();
                ExecucaoPipeline execucao = context.getExecucaoPipeline();
                Versao versao = context.getVersao();

                if (features == null || features.isEmpty()) {
                        context.setState(null);
                        if (execucao != null) {
                                fecharExecucao(execucao, 0, context.getTotalRejeitados());
                                execucaoRepository.save(execucao);
                        }
                        return;
                }

                // 1) Zona tratada (US09.03): arquivo + caminho + hash + movimentação.
                // Gravada antes da transação do banco. Se o banco falhar depois, o objeto
                // fica na zona tratada; ignore objetos de execuções com status FALHOU.
                if (execucao != null) {

                        List<Map<String, Object>> linhas = new ArrayList<>(features.size());

                        for (SimpleFeature f : features) {
                                linhas.add(FeatureMapper.paraMapa(f));
                        }

                        SpringContextHolder
                                        .getBean(ZonaTratadaService.class)
                                        .gravar(context, "EMBARGO", linhas);
                }

                // 2) Banco, em uma única transação
                TransactionStatus status = transactionManager.getTransaction(
                                new DefaultTransactionDefinition());

                List<Embargo> batchList = new ArrayList<>();

                int totalSalvos = 0;

                try {

                        for (SimpleFeature feature : features) {

                                Geometry geom = (Geometry) feature.getDefaultGeometry();

                                if (geom == null) {
                                        continue;
                                }

                                geom.setSRID(SRID_SIRGAS_2000);

                                Embargo embargo = new Embargo();

                                embargo.setGeometria(geom);
                                embargo.setArquivoBruto(context.getArquivoBruto());
                                embargo.setVersao(versao);

                                Object idAtributo = feature.getAttribute("num_tad");

                                if (idAtributo == null) {
                                        idAtributo = feature.getAttribute("NUM_TAD");
                                }

                                embargo.setIdentificador(
                                                idAtributo != null
                                                                ? idAtributo.toString()
                                                                : feature.getID());

                                batchList.add(embargo);
                                totalSalvos++;

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

                        // Publicação da versão, vinculada à execução (antes só o fluxo
                        // de imóvel fazia isso)
                        versao.setSituacao("VIGENTE");
                        versao.setDataPublicacao(LocalDateTime.now());
                        versao.setExecucao(execucao);
                        versaoRepository.save(versao);

                        ConjuntoDados conjunto = versao.getConjunto();
                        conjunto.setVersaoVigente(versao);
                        conjunto.setSituacao("PUBLICADO");
                        conjuntoRepository.save(conjunto);

                        if (execucao != null) {
                                fecharExecucao(execucao, totalSalvos, context.getTotalRejeitados());
                                execucaoRepository.save(execucao);
                        }

                        transactionManager.commit(status);

                } catch (Exception e) {

                        if (!status.isCompleted()) {
                                transactionManager.rollback(status);
                        }

                        // O status FALHOU da execução é gravado pelo BaseTreatment.execute(),
                        // FORA da transação revertida (antes era salvo dentro dela e se perdia).
                        throw new RuntimeException(
                                        "Erro ao salvar embargos",
                                        e);
                }

                context.setState(null);
        }

        private void fecharExecucao(ExecucaoPipeline execucao, int processados, int rejeitados) {

                execucao.setStatus("CONCLUIDA");

                execucao.setFim(LocalDateTime.now());

                execucao.setRegistrosProcessados((long) processados);

                execucao.setRegistrosRejeitados((long) rejeitados);

                if (execucao.getInicio() != null) {
                        execucao.setDuracaoSeg(
                                        Duration.between(
                                                        execucao.getInicio(),
                                                        LocalDateTime.now())
                                                        .getSeconds());
                }
        }
}
