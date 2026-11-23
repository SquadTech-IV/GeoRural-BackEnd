package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;

// Uma barra do histograma. Intervalo e [de, ate), menos a ultima, que inclui o ate.
public record BarraHistogramaResponse(
        int ordem,
        BigDecimal de,
        BigDecimal ate,
        String rotulo,
        long quantidade,
        BigDecimal percentualDoTotal
) {}
