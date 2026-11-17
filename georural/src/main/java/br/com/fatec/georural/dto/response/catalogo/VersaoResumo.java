package br.com.fatec.georural.dto.response.catalogo;

import java.time.LocalDateTime;

// Versao de um conjunto, no nivel de detalhe do catalogo.
public record VersaoResumo(
        Long id,
        Integer numero,
        String competencia,
        String situacao,
        Long execucaoId,
        LocalDateTime criadaEm,
        LocalDateTime publicadaEm
) {}
