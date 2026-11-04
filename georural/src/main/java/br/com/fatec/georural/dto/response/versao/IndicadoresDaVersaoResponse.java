package br.com.fatec.georural.dto.response.versao;

import java.util.List;

// contagem por situacao de cada indicador da versao, com a vigente ao lado para comparar
public record IndicadoresDaVersaoResponse(
        Long versaoId,
        Long versaoNumero,
        String versaoSituacao,
        Long versaoComparadaId,
        Long versaoComparadaNumero,
        List<IndicadorContagem> indicadores) {

    public record IndicadorContagem(
            String sigla,
            String nome,
            long calculados,
            long calculadosComparados,
            List<PorSituacao> situacoes) {
    }

    // quantos imoveis cairam nesta situacao nesta versao e na que esta valendo
    public record PorSituacao(
            String situacao,
            long quantidade,
            long quantidadeComparada) {
    }
}
