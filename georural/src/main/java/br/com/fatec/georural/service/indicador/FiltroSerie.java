package br.com.fatec.georural.service.indicador;

import java.math.BigDecimal;

// Os mesmos filtros da tabela da tela, menos paginacao e ordenacao.
public record FiltroSerie(
        String indicador,
        Long versaoId,
        String competencia,
        Long municipioId,
        String municipio,
        String codigoCar,
        String situacao,
        Boolean vigente,
        BigDecimal valorMinimo,
        BigDecimal valorMaximo,
        Integer qtdFaixas
) {}
