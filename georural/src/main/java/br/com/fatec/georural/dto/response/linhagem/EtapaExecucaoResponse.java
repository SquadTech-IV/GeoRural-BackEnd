package br.com.fatec.georural.dto.response.linhagem;

import java.time.LocalDateTime;

// Uma execucao do pipeline, como aparece na linhagem.
public record EtapaExecucaoResponse(
        Long id,
        String etapa,
        String status,
        String dagRunId,
        String mensagem,
        String disparadoPor,
        Long arquivoId,
        Long versaoGeradaId,
        Long registrosRecebidos,
        Long registrosProcessados,
        Long registrosAprovados,
        Long registrosRejeitados,
        LocalDateTime inicio,
        LocalDateTime fim,
        Long duracaoSegundos
) {}
