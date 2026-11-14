package br.com.fatec.georural.dto.response.linhagem;

import java.util.List;

// As etapas que rodaram no mesmo DAG run.
public record PipelineLinhagemResponse(
        String dagRunId,
        List<EtapaExecucaoResponse> etapas
) {}
