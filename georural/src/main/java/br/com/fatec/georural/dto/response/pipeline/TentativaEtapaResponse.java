package br.com.fatec.georural.dto.response.pipeline;

import java.time.LocalDateTime;

// Uma tentativa de uma etapa: uma linha de EXECUCAO_PIPELINE.
public record TentativaEtapaResponse(
        Long execucaoId,
        int tentativa,
        String situacao,
        String mensagem,
        String disparadoPor,
        long recebidos,
        long aprovados,
        long rejeitados,
        Long versaoGeradaId,
        LocalDateTime inicio,
        LocalDateTime fim,
        Long duracaoSeg) {
}
