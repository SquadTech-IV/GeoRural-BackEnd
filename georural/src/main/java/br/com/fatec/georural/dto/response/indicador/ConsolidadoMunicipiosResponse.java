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
        List<MunicipioConsolidadoResponse> municipios) {

    public record Indicador(
            Long id,
            String sigla,
            String nome,
            String unidade) {
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
}
