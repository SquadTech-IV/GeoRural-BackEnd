package br.com.fatec.georural.dto.request.pipeline;

import java.util.Map;

public record ExecucaoPipelineRequest(
        Long conjuntoId,
        Long arquivoBrutoId,
        Long usuarioId,
        String competencia,
        String dagRunId,
        Map<String, String> parametros) {
}
