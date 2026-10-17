package br.com.fatec.georural.dto.response.imovel;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.math.BigDecimal;

public record IndicadorVigenteResponse(
        String sigla,
        String nome,
        String unidade,
        String situacao,
        BigDecimal valorPercentual,
        BigDecimal valorHectares,
        BigDecimal valorAbsoluto,
        String versaoRegra,
        @JsonRawValue String features
) {}