package br.com.fatec.georural.service.treatment.orchestrator;

import java.io.File;

import org.springframework.stereotype.Service;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.service.treatment.DetectorTipoCamada;
import br.com.fatec.georural.service.treatment.audit.ExecucaoPipelineService;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.GeoDatasetType;
import br.com.fatec.georural.service.treatment.factory.TreatmentFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestrador de validação e tratamento do pipeline (US#08 - T09).
 * - Executa individualmente as regras de validação registro a registro sem interromper o lote por inconsistência de dados.
 * - Registros reprovados são encaminhados para a Quarentena.
 * - Distingue rejeição de dados de erros técnicos inesperados (marcando a execução como FALHOU sem gerar rejeições falsas).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TreatmentOrchestrator {

    private final DetectorTipoCamada detector;
    private final TreatmentFactory factory;
    private final ExecucaoPipelineService execucaoPipelineService;

    public void execute(File file) {
        GeoDatasetType tipo = detector.detectar(file);
        BaseTreatment treatment = factory.create(tipo, file);
        treatment.execute();
    }

    /**
     * Processa e orquestra a validação do arquivo bruto vinculado a uma execução do pipeline.
     */
    public ExecucaoPipeline processarValidacao(ArquivoBruto arquivoBruto, File arquivoFisico) {
        ExecucaoPipeline execucao = execucaoPipelineService.iniciar(
                arquivoBruto.getConjunto(),
                arquivoBruto,
                "VALIDACAO"
        );

        try {
            GeoDatasetType tipo = detector.detectar(arquivoFisico);
            BaseTreatment treatment = factory.create(tipo, arquivoFisico);

            treatment.setArquivoBruto(arquivoBruto);
            treatment.setExecucaoPipeline(execucao);

            log.info("Iniciando orquestração da validação da execução id={}, arquivoBruto id={}", 
                    execucao.getId(), arquivoBruto.getId());

            treatment.execute();

            treatment.encerrarValidacao();

            execucaoPipelineService.finalizarComSucesso(
                    execucao,
                    treatment.getTotalCarregado(),
                    treatment.getTotalRejeitados()
            );

            log.info("Validação concluída com sucesso para execução id={}. Recebidos: {}, Aprovados: {}, Rejeitados: {}",
                    execucao.getId(),
                    treatment.getTotalCarregado(),
                    treatment.getTotalCarregado() - treatment.getTotalRejeitados(),
                    treatment.getTotalRejeitados());

            return execucao;

        } catch (Exception e) {
            log.error("Erro técnico inesperado durante a execução da validação id={}: {}", execucao.getId(), e.getMessage(), e);
            execucaoPipelineService.registrarFalhaTecnica(execucao, e);
            throw e;
        }
    }
}