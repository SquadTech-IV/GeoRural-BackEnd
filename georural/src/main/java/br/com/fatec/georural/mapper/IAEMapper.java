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
                        row[2] != null ? row[2].toString() : null,
                        toBigDecimal(row[3])
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

    private BigDecimal toBigDecimal(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO;
        }
        if (valor instanceof BigDecimal bd) {
            return bd;
        }
        if (valor instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }

        String texto = valor.toString().trim().replace(",", ".");
        if (texto.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Valor numérico inválido: '" + valor + "' (tipo " + valor.getClass().getName() + ")", e);
        }
    }
}