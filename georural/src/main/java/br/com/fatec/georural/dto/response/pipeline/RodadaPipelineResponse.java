package br.com.fatec.georural.dto.response.pipeline;

import br.com.fatec.georural.dto.response.linhagem.EtapaExecucaoResponse;

import java.time.LocalDateTime;
import java.util.List;

// Uma rodada do pipeline: todas as etapas de um mesmo dag_run_id.
public record RodadaPipelineResponse(
        String dagRunId,
        Long conjuntoId,
        String conjunto,
        String tipoCamada,
        String fonteSigla,
        Long arquivoId,
        String arquivo,
        String competencia,
        boolean automatica,
        String situacao,
        String etapaComFalha,
        String mensagem,
        int totalEtapas,
        int etapasConcluidas,
        int etapasFalhadas,
        Long registrosRecebidos,
        Long registrosProcessados,
        Long registrosAprovados,
        Long registrosRejeitados,
        Long versaoGeradaId,
        LocalDateTime inicio,
        LocalDateTime fim,
        Long duracaoSegundos,
        List<EtapaExecucaoResponse> etapas
) {}
