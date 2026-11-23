package br.com.fatec.georural.dto.response.pipeline;

import java.time.LocalDateTime;

// Falha recente do pipeline.
public record FalhaRecenteResponse(
        Long execucaoId,
        String etapa,
        String dagRunId,
        Long conjuntoId,
        String conjunto,
        String arquivo,
        String mensagem,
        LocalDateTime quando
) {}
