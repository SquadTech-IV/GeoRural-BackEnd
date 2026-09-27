package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import br.com.fatec.georural.dto.response.EmbargoIntersectanteResponse;

public class IAEResponse extends ResultadoIndicadorResponse {
    private final List<EmbargoIntersectanteResponse> embargosIntersectantes;

    public IAEResponse(Long imovelId, String indicadorSigla, BigDecimal valorPercentual, BigDecimal valorHectares, BigDecimal valorAbsoluto, LocalDateTime dataCalculo, List<EmbargoIntersectanteResponse> embargosIntersectantes) {
        super(imovelId, indicadorSigla, valorPercentual, valorHectares, valorAbsoluto, dataCalculo);
        this.embargosIntersectantes = embargosIntersectantes;
    }
    
    public List<EmbargoIntersectanteResponse> getEmbargosIntersectantes() {
        return embargosIntersectantes;
    }
}
