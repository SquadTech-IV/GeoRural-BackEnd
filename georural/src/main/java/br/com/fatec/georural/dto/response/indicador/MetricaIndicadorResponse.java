package br.com.fatec.georural.dto.response.indicador;

import java.time.LocalDateTime;
import java.util.List;

// Metricas de um indicador numa versao.
public record MetricaIndicadorResponse(
        String indicador,
        String indicadorNome,
        String unidade,
        Long versaoId,
        Integer versaoNumero,
        String competencia,
        long resultados,
        long imoveis,
        FaixaValorResponse percentual,
        FaixaValorResponse hectares,
        List<ContagemSituacaoResponse> situacoes,
        LocalDateTime ultimoCalculo
) {}
