package br.com.fatec.georural.service.exportacao;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LinhaResultado(
        String codigoCar,
        String municipio,
        String codigoIbge,
        BigDecimal areaTotalHa,
        String indicador,
        String unidade,
        BigDecimal valorPercentual,
        BigDecimal valorHectares,
        BigDecimal valorAbsoluto,
        String situacao,
        OffsetDateTime dataCalculo,
        String detalhes,
        String geometria
) {}
