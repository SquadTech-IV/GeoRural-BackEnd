package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ResultadoIDesmat(
        String versaoRegra,
        List<SupressaoPorMarco> marcos
) {
    public record SupressaoPorMarco(
            String marco,
            LocalDate data,
            BigDecimal areaHa,
            BigDecimal percentual
    ) {
    }
}