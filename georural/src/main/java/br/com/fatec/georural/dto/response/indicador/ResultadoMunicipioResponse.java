package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Resultado agregado de um indicador para um municipio.
public record ResultadoMunicipioResponse(
        Long id,
        Long municipioId,
        String codigoIbge,
        String municipio,
        String uf,
        String indicador,
        String indicadorNome,
        String unidade,
        Long versaoId,
        Integer versaoNumero,
        String competencia,
        BigDecimal valorAgregado,
        String situacao,
        boolean vigente,
        LocalDateTime calculadoEm,
        Object detalhes
) {}
