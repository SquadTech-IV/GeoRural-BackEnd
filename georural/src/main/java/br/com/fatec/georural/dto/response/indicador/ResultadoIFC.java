package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;

public record ResultadoIFC(
        String versaoRegra,
        int periodoMeses,
        BigDecimal fator,
        long focosNoPeriodo,
        BigDecimal indice
) {
}