package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;

// Parametro de uma regra de calculo.
public record ParametroRegraResponse(
        String nome,
        String rotulo,
        String tipo,
        String unidade,
        String valor,
        boolean editavel,
        BigDecimal minimo,
        BigDecimal maximo
) {}
