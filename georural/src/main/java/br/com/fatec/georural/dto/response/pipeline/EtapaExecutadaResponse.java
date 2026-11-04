package br.com.fatec.georural.dto.response.pipeline;

import java.time.LocalDateTime;

public record EtapaExecutadaResponse(
        Long execucaoId,
        String etapa,
        int ordem,
        String dagRunId,
        String situacao,
        long recebidos,
        long aprovados,
        long rejeitados,
        String mensagem,
        Long versaoGerada,
        LocalDateTime inicio,
        LocalDateTime fim,
        Long duracaoSeg) {
}
