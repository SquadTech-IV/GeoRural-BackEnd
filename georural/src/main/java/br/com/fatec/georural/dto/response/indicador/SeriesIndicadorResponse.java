package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.util.List;

// Os dois graficos da tela da US#18, ja agregados: o front nao soma nada.
public record SeriesIndicadorResponse(
        String indicador,
        String indicadorNome,
        String unidade,
        String sentido,
        long resultados,
        long imoveis,
        BigDecimal escalaMinima,
        BigDecimal escalaMaxima,
        String origemDaEscala,
        List<ContagemSituacaoResponse> composicao,
        List<BarraHistogramaResponse> histograma,
        long abaixoDaEscala,
        long acimaDaEscala,
        List<String> avisos
) {}
