package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ResultadoIndicadorResponse {
    private final Long imovelId;
    private final String indicadorSigla;
    private final BigDecimal valorPercentual;
    private final BigDecimal valorHectares;
    private final BigDecimal valorAbsoluto;
    private final LocalDateTime dataCalculo;

    public ResultadoIndicadorResponse(Long imovelId, String indicadorSigla, BigDecimal valorPercentual, BigDecimal valorHectares, BigDecimal valorAbsoluto, LocalDateTime dataCalculo) {
        this.imovelId = imovelId;
        this.indicadorSigla = indicadorSigla;
        this.valorPercentual = valorPercentual;
        this.valorHectares = valorHectares;
        this.valorAbsoluto = valorAbsoluto;
        this.dataCalculo = dataCalculo;
    }

    Long getImovelId() {
        return imovelId;
    }

    String getIndicadorSigla() {
        return indicadorSigla;
    }

    BigDecimal getValorPercentual() {
        return valorPercentual;
    }

    BigDecimal getValorHectares() {
        return valorHectares;
    }

    BigDecimal getValorAbsoluto() {
        return valorAbsoluto;
    }

    LocalDateTime getDataCalculo() {
        return dataCalculo;
    }
}
