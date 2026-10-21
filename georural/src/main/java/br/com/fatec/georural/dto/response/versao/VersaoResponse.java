package br.com.fatec.georural.dto.response.versao;

import java.time.LocalDateTime;

public record VersaoResponse(
        Long id,
        Long conjuntoId,
        Long numero,
        String situacao,
        String competencia,
        LocalDateTime dataPublicacao,
        Long execucaoId
) {
}