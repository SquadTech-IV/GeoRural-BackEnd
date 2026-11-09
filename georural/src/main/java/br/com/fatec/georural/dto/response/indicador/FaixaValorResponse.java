package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;

// Resumo estatistico de um valor dentro de um grupo.
public record FaixaValorResponse(
        BigDecimal minimo,
        BigDecimal maximo,
        BigDecimal media,
        BigDecimal mediana,
        BigDecimal soma
) {}
