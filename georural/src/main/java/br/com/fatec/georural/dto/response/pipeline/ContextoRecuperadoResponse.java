package br.com.fatec.georural.dto.response.pipeline;

import java.util.List;
import java.util.Map;

// O que o servico leu da rodada para montar o contexto, para quem chamou conferir.
public record ContextoRecuperadoResponse(
        Long conjuntoId,
        String conjunto,
        Long arquivoBrutoId,
        String arquivo,
        String competencia,
        Long usuarioId,
        Map<String, String> parametros,
        List<String> parametrosInformados,
        int etapasDaRodada) {
}
