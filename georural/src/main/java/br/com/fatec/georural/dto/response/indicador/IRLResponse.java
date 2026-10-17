package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class IRLResponse extends ResultadoIndicadorResponse {

    private final String situacao;
    private final BigDecimal minimoPercent;
    private final BigDecimal areaMinimaHa;
    private final String versaoRegra;

    public IRLResponse(Long imovelId, String indicadorSigla,
                       BigDecimal valorPercentual, BigDecimal valorHectares, BigDecimal valorAbsoluto,
                       LocalDateTime dataCalculo,
                       String situacao, BigDecimal minimoPercent, BigDecimal areaMinimaHa, String versaoRegra) {
        super(imovelId, indicadorSigla, valorPercentual, valorHectares, valorAbsoluto, dataCalculo);
        this.situacao = situacao;
        this.minimoPercent = minimoPercent;
        this.areaMinimaHa = areaMinimaHa;
        this.versaoRegra = versaoRegra;
    }

    public String getSituacao() {
        return situacao;
    }

    public BigDecimal getMinimoPercent() {
        return minimoPercent;
    }

    public BigDecimal getAreaMinimaHa() {
        return areaMinimaHa;
    }

    public String getVersaoRegra() {
        return versaoRegra;
    }
}