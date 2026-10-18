package br.com.fatec.georural.dto.response.imovel;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.math.BigDecimal;
import java.util.List;

public record ImovelIndicadoresResponse(
        Long id,
        String codigoCar,
        String municipio,
        BigDecimal areaTotal,
        @JsonRawValue String geometria,
        List<IndicadorVigenteResponse> indicadores
) {}