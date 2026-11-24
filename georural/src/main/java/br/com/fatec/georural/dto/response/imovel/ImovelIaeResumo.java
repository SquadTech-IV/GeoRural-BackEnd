package br.com.fatec.georural.dto.response.imovel;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ImovelIaeResumo(
        BigDecimal valorPercentual,
        BigDecimal valorHectares,
        BigDecimal valorAbsoluto,
        LocalDateTime dataCalculo,
        @JsonRawValue String detalhes
) {}