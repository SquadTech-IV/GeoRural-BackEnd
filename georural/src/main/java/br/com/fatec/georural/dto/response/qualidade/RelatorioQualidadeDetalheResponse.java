package br.com.fatec.georural.dto.response.qualidade;

import java.util.List;
import java.util.Map;

// Relatorio de qualidade com as regras violadas e uma amostra da quarentena.
public record RelatorioQualidadeDetalheResponse(
        RelatorioQualidadeResponse relatorio,
        List<ViolacaoRegraResponse> regrasVioladas,
        List<ContagemRotuloResponse> porEtapa,
        List<ContagemRotuloResponse> porSituacao,
        List<ContagemRotuloResponse> porSeveridade,
        int tamanhoDaAmostra,
        List<ItemQuarentenaResponse> amostra,
        Map<String, String> links
) {}
