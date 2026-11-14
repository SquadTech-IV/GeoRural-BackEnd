package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;

// Quantos resultados cairam em cada situacao.
public record ContagemSituacaoResponse(
        String situacao,
        long quantidade,
        BigDecimal percentualDoTotal
) {}
