package br.com.fatec.georural.dto.response.iapp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IappListaItemResponse(
        Long iappId,
        Long imovelId,
        String codigoCar,
        String nomeImovel,
        Long municipioId,
        String municipio,
        Long versaoId,
        BigDecimal appTotalHa,
        BigDecimal appVegetadaHa,
        BigDecimal passivoHa,
        BigDecimal iappPct,
        BigDecimal areaImovelHa,
        String status,
        String mensagem,
        String vigente,
        LocalDateTime dataCalculo
) {}