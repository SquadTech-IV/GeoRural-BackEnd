package br.com.fatec.georural.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IaeResultadoResponse(
        String sigla,
        BigDecimal valorPercentual,
        BigDecimal valorHectares,
        BigDecimal valorAbsoluto,
        LocalDateTime dataCalculo) {
}