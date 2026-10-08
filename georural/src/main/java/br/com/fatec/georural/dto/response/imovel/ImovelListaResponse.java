package br.com.fatec.georural.dto.response.imovel;

import java.math.BigDecimal;

public record ImovelListaResponse(
        Long id,
        String codigoCar,
        String municipio,
        BigDecimal areaTotal,
        BigDecimal iaePercentual   // null se o IAE ainda nao foi calculado
) {}