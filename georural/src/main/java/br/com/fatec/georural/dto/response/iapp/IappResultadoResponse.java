package br.com.fatec.georural.dto.response.iapp;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IappResultadoResponse(
        Long iappId,
        Long imovelId,
        String codigoCar,
        String nomeImovel,
        Long municipioId,
        String municipio,
        Long versaoId,
        Integer versaoNumero,
        Long appVersaoId,
        Long vegetacaoVersaoId,
        Long execucaoId,
        Long regraId,
        String regraVersao,
        BigDecimal appTotalHa,
        BigDecimal appVegetadaHa,
        BigDecimal passivoHa,
        BigDecimal iappPct,
        BigDecimal areaImovelHa,
        String filtroTiposApp,
        String filtroClassesVegetacao,
        String competenciaApp,
        String competenciaVegetacao,
        String status,
        String mensagem,
        String vigente,
        LocalDateTime dataCalculo,
        @JsonRawValue String appVegetadaGeoJson,
        @JsonRawValue String passivoGeoJson
) {}