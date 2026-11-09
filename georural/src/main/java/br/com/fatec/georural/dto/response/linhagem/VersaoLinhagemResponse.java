package br.com.fatec.georural.dto.response.linhagem;

import java.time.LocalDateTime;

// Versao do conjunto a que o dado pertence.
public record VersaoLinhagemResponse(
        Long id,
        Integer numero,
        String competencia,
        String situacao,
        String hash,
        String justificativa,
        Long conjuntoId,
        String conjunto,
        String tipoCamada,
        Long execucaoQueGerou,
        String publicadaPor,
        LocalDateTime criadaEm,
        LocalDateTime publicadaEm
) {}
