package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;

public record ResultadoIRL(
        String versaoRegra,
        BigDecimal minimoPercent,
        BigDecimal percentualRL,
        BigDecimal areaMinimaHa,
        BigDecimal saldoHa
) {
}