package br.com.fatec.georural.dto.response.catalogo;

import java.time.LocalDateTime;

// Uma versao no historico do conjunto, com quantos registros ela gravou.
public record VersaoDetalheResponse(
        Long id,
        Integer numero,
        String competencia,
        String situacao,
        Long execucaoId,
        String justificativa,
        boolean vigente,
        Long registros,
        LocalDateTime criadaEm,
        LocalDateTime publicadaEm
) {}
