package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import br.com.fatec.georural.dto.response.EmbargoIntersectanteResponse;

public class IAEResponse extends ResultadoIndicadorResponse {
    private final List<EmbargoIntersectanteResponse> embargosIntersectantes;

    public IAEResponse(
        Long imovelId, 
        String municipio,
        String codigoCar,
        String indicadorSigla, 
        BigDecimal valorPercentual, // Total dos embargos
        BigDecimal valorHectares, // Total dos embargos
        BigDecimal valorAbsoluto,
        LocalDateTime dataCalculo, 
        List<EmbargoIntersectanteResponse> embargosIntersectantes // Lista de embargos para demonstração
    ) {
        super(imovelId, municipio, codigoCar, indicadorSigla, valorPercentual, valorHectares, valorAbsoluto, dataCalculo);
        this.embargosIntersectantes = embargosIntersectantes;
    }
    
    public List<EmbargoIntersectanteResponse> getEmbargosIntersectantes() {
        return embargosIntersectantes;
    }
}
