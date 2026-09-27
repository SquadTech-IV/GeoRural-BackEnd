package br.com.fatec.georural.mapper;

import br.com.fatec.georural.dto.response.EmbargoIntersectanteResponse;
import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.entity.ResultadoIndicador;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class IAEMapper {

    public IAEResponse toResponse(ResultadoIndicador resultado, List<Object[]> embargosRaw) {
        List<EmbargoIntersectanteResponse> embargos = embargosRaw.stream()
            .map(row -> new EmbargoIntersectanteResponse(
                ((Number) row[1]).longValue(),
                (String) row[2],
                new BigDecimal(row[3].toString())
            ))
            .toList();

        return new IAEResponse(
            resultado.getImovel().getId(),
            resultado.getIndicador().getSigla(),
            resultado.getValorPercentual(),
            resultado.getValorHectares(),
            resultado.getValorAbsoluto(),
            resultado.getDataCalculo(),
            embargos
        );
    }
}