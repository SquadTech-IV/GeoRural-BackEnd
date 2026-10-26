package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ResultadoIndicadorResponse {
    private final Long imovelId;
    private final String municipio;
    private final String codigoCar;
    private final String indicadorSigla;
    private final BigDecimal valorPercentual;
    private final BigDecimal valorHectares;
    private final BigDecimal valorAbsoluto;
    private final LocalDateTime dataCalculo;

    public ResultadoIndicadorResponse(
            Long imovelId, 
            String municipio,
            String codigoCar,
            String indicadorSigla, 
            BigDecimal valorPercentual, 
            BigDecimal valorHectares, 
            BigDecimal valorAbsoluto, 
            LocalDateTime dataCalculo
    ) {
        this.imovelId = imovelId;
        this.municipio = municipio;
        this.codigoCar = codigoCar;
        this.indicadorSigla = indicadorSigla;
        this.valorPercentual = valorPercentual;
        this.valorHectares = valorHectares;
        this.valorAbsoluto = valorAbsoluto;
        this.dataCalculo = dataCalculo;
    }

    public Long getImovelId() {
        return imovelId;
    }

    public String getMunicipio() {
        return municipio;
    }

    public String getCodigoCar() {
        return codigoCar;
    }

    public String getIndicadorSigla() {
        return indicadorSigla;
    }

    public BigDecimal getValorPercentual() {
        return valorPercentual;
    }

    public BigDecimal getValorHectares() {
        return valorHectares;
    }

    public BigDecimal getValorAbsoluto() {
        return valorAbsoluto;
    }

    public LocalDateTime getDataCalculo() {
        return dataCalculo;
    }
}
