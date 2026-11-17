package br.com.fatec.georural.dto.response.pipeline;

import java.time.LocalDateTime;

// A ultima vez que essa etapa rodou nessa rodada.
public record TentativaAnteriorResponse(
        Long execucaoId,
        int tentativa,
        String situacao,
        String mensagem,
        LocalDateTime inicio,
        LocalDateTime fim,
        Long duracaoSeg) {
}
