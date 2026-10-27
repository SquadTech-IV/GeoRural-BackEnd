package br.com.fatec.georural.service.treatment.reserva_legal.state;

import java.math.BigDecimal;
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
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.ReservaLegal;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.ReservaLegalRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.treatment.common.FeatureMapper;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.storage.ZonaTratadaService;
import jakarta.persistence.EntityManager;

/**
 * Persiste as feições aprovadas de Reserva Legal: grava na zona tratada (OCI + MOVIMENTACAO_ZONA) e na
 * tabela RESERVA_LEGAL, ligando cada RL ao imóvel pelo recibo (= CAR). Em seguida publica a versão e fecha
 * a execução. Segue o mesmo molde do EmbargoSavingState, mas resolve o imóvel por CAR.
 */
public class ReservaLegalSavingState implements TreatmentState {

    private static final int SRID_SIRGAS_2000 = 4674;
    private static final int BATCH_SIZE = 1000;

    @Override
    public void execute(BaseTreatment context) {

        ReservaLegalRepository reservaRepository = SpringContextHolder.getBean(ReservaLegalRepository.class);
        ImovelRuralRepository imovelRepository = SpringContextHolder.getBean(ImovelRuralRepository.class);
        EntityManager entityManager = SpringContextHolder.getBean(EntityManager.class);
        PlatformTransactionManager transactionManager = SpringContextHolder.getBean(PlatformTransactionManager.class);
        ExecucaoPipelineRepository execucaoRepository = SpringContextHolder.getBean(ExecucaoPipelineRepository.class);
        VersaoRepository versaoRepository = SpringContextHolder.getBean(VersaoRepository.class);
        ConjuntoDadosRepository conjuntoRepository = SpringContextHolder.getBean(ConjuntoDadosRepository.class);

        List<SimpleFeature> aprovadas = context.getFeatures();
        ExecucaoPipeline execucao = context.getExecucaoPipeline();
        Versao versao = context.getVersao();

        // 1) Encerra a validação: consolida VALIDACAO e grava quarentena.json na OCI se houve rejeição
        context.encerrarValidacao();

        if (aprovadas == null || aprovadas.isEmpty()) {
            if (execucao != null) {
                fecharExecucao(execucao, 0, context.getTotalRejeitados());
                execucaoRepository.save(execucao);
            }
            context.setState(null);
            return;
        }

        // 2) Zona Tratada (OCI + MOVIMENTACAO_ZONA) apenas para os aprovados
        if (execucao != null) {
            List<Map<String, Object>> linhas = new ArrayList<>(aprovadas.size());
            for (SimpleFeature f : aprovadas) {
                linhas.add(FeatureMapper.paraMapa(f));
            }
            SpringContextHolder
                    .getBean(ZonaTratadaService.class)
                    .gravar(context, "RESERVA_LEGAL", linhas);
        }

        // 3) Persistência das RL no Oracle (ligadas ao imóvel por CAR) e publicação da versão
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        List<ReservaLegal> batchList = new ArrayList<>();
        int totalSalvos = 0;

        try {
            for (SimpleFeature feature : aprovadas) {
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                if (geom == null) {
                    continue;
                }
                geom.setSRID(SRID_SIRGAS_2000);

                String recibo = lerRecibo(feature);
                ImovelRural imovel = (recibo != null)
                        ? imovelRepository.findByCodigoCar(recibo).orElse(null)
                        : null;
                if (imovel == null) {
                    // Já filtrado no FieldsValidationState; defesa extra para não violar o FK imo_id (NOT NULL).
                    continue;
                }

                ReservaLegal reserva = new ReservaLegal();
                reserva.setImovel(imovel);
                reserva.setGeometria(geom);
                reserva.setArquivoBruto(context.getArquivoBruto());
                reserva.setVersao(versao);
                reserva.setAreaHa(extrairArea(feature));

                batchList.add(reserva);
                totalSalvos++;

                if (batchList.size() >= BATCH_SIZE) {
                    reservaRepository.saveAll(batchList);
                    entityManager.flush();
                    entityManager.clear();
                    batchList.clear();
                }
            }

            if (!batchList.isEmpty()) {
                reservaRepository.saveAll(batchList);
                entityManager.flush();
                entityManager.clear();
            }

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
            throw new RuntimeException("Erro ao salvar reservas legais", e);
        }

        context.setState(null);
    }

    private String lerRecibo(SimpleFeature feature) {
        Object recibo = feature.getAttribute("recibo");
        if (recibo == null) {
            recibo = feature.getAttribute("RECIBO");
        }
        return recibo != null ? recibo.toString().trim() : null;
    }

    private BigDecimal extrairArea(SimpleFeature feature) {
        Object area = feature.getAttribute("area");
        if (area == null) {
            area = feature.getAttribute("AREA");
        }
        if (area == null) {
            return null;
        }
        try {
            return new BigDecimal(area.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void fecharExecucao(ExecucaoPipeline execucao, int processados, int rejeitados) {
        execucao.setStatus(rejeitados > 0 ? "COM_RESSALVA" : "CONCLUIDA");
        execucao.setFim(LocalDateTime.now());
        execucao.setRegistrosProcessados((long) (processados + rejeitados));
        execucao.setRegistrosAprovados((long) processados);
        execucao.setRegistrosRejeitados((long) rejeitados);
        if (execucao.getInicio() != null) {
            execucao.setDuracaoSeg(Duration.between(execucao.getInicio(), LocalDateTime.now()).getSeconds());
        }
    }
}
