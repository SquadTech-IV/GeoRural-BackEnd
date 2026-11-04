package br.com.fatec.georural.dto.response.imovel;

import java.math.BigDecimal;

public record ImovelListaResponse(
        Long id,
        String codigoCar,
        String nome,
        String rotulo,
        String municipio,
        String codigoIbge,
        BigDecimal areaTotal,
        BigDecimal iaePercentual
) {}
