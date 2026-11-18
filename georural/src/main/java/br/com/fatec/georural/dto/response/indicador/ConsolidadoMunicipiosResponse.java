package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.util.List;

/** O consolidado de um indicador em todos os municipios. */
public record ConsolidadoMunicipiosResponse(
        Indicador indicador,
        // as versoes de onde os resultados vieram; normalmente uma
        List<Versao> versoes,
        // true quando a consulta usou rmu_vigente = 'S'
        boolean vigente,
        // a situacao que conta como boa neste indicador (CONFORME, SEM_EMBARGO...)
        String situacaoBoa,
        int totalMunicipios,
        int municipiosComDados,
        int municipiosSemDados,
        Long imoveisConsiderados,
        Resumo resumo,
        // como a lista saiu ordenada, para a tela mostrar o criterio em uso
        String ordenacao,
        // de onde saiu a escala das faixas
        Escala escala,
        List<Faixa> faixas,
        // o que a equipe precisa saber sobre esta resposta
        List<String> avisos,
        List<MunicipioConsolidadoResponse> municipios) {

    public record Indicador(
            Long id,
            String sigla,
            String nome,
            String unidade,
            // MAIOR_MELHOR ou MENOR_MELHOR: e o que define o que e "pior"
            String sentido) {
    }

    public record Versao(
            Long id,
            Long numero,
            String competencia,
            String situacao,
            Long conjuntoId,
            String conjunto) {
    }

    /** Escala do indicador entre os municipios com dados, para legenda e mapa. */
    public record Resumo(
            BigDecimal minimo,
            BigDecimal maximo,
            BigDecimal media,
            BigDecimal mediana) {
    }

    /** Onde as faixas comecam e terminam, e de onde esse intervalo veio. */
    public record Escala(
            BigDecimal minimo,
            BigDecimal maximo,
            // INDICADOR (escala cadastrada), DADOS (minimo e maximo da consulta),
            // NENHUMA ou SEM_DADOS
            String origem,
            String estrategia) {
    }

    /**
     * Uma faixa da comparacao. ordem 1 e sempre a de valor mais baixo;
     * posicaoNaQualidade 1 e sempre a pior, independente do sentido do indicador.
     * O intervalo e [de, ate), menos a ultima faixa, que inclui o ate.
     */
    public record Faixa(
            int ordem,
            String rotulo,
            BigDecimal de,
            BigDecimal ate,
            int posicaoNaQualidade,
            long quantidade) {
    }
}
