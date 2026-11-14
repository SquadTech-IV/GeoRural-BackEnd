package br.com.fatec.georural.dto.response.pipeline;

import java.time.LocalDateTime;

// Uma execucao de etapa do pipeline.
public record ExecucaoItemResponse(
        Long id,
        String etapa,
        String status,
        String dagRunId,
        boolean automatica,
        Long conjuntoId,
        String conjunto,
        String tipoCamada,
        String fonteSigla,
        Long arquivoId,
        String arquivo,
        String competencia,
        String disparadoPor,
        String mensagem,
        Long versaoGeradaId,
        Long registrosRecebidos,
        Long registrosProcessados,
        Long registrosAprovados,
        Long registrosRejeitados,
        LocalDateTime inicio,
        LocalDateTime fim,
        Long duracaoSegundos
) {}
