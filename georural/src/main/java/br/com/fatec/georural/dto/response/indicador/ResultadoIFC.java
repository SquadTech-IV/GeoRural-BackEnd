package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;

// indice e nulo quando o imovel nao tem area valida (nao ha como proporcionar)
public record ResultadoIFC(
        String versaoRegra,
        int periodoMeses,
        BigDecimal fator,
        long focosNoPeriodo,
        BigDecimal indice
) {
}