package br.com.fatec.georural.dto.response.linhagem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LinhagemResultadoItemResponse(
        Long id,
        String codigoCar,
        String municipio,
        String indicadorSigla,
        BigDecimal valorPercentual,
        BigDecimal valorHectares,
        Long versaoNumero,
        LocalDateTime publicadoEm
) {}
