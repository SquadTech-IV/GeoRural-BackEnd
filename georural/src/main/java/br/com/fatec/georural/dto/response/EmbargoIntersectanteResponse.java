package br.com.fatec.georural.dto.response;

import java.math.BigDecimal;

public record EmbargoIntersectanteResponse(
    Long embargoId,
    String identificador,
    BigDecimal areaSobrepostaM2
) {}
