package br.com.fatec.georural.dto.response.imovel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ImovelIaeResumo(
        BigDecimal valorPercentual,
        BigDecimal valorHectares,
        BigDecimal valorAbsoluto,
        LocalDateTime dataCalculo
) {}