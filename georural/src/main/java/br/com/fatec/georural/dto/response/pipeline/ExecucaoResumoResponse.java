package br.com.fatec.georural.dto.response.pipeline;

import java.time.LocalDateTime;

// uma rodada do pipeline: as etapas de um mesmo dag_run_id resumidas em uma linha
public record ExecucaoResumoResponse(
        String dagRunId,
        Long conjuntoId,
        String conjuntoNome,
        Long arquivoBrutoId,
        String arquivoNome,
        String canalEntrada,
        boolean automatica,
        String situacao,
        String etapaComFalha,
        String mensagem,
        int etapas,
        long recebidas,
        long rejeitadas,
        LocalDateTime inicio,
        LocalDateTime fim,
        Long versaoId,
        Long versaoNumero,
        String versaoSituacao,
        Long execucaoId) {
}
