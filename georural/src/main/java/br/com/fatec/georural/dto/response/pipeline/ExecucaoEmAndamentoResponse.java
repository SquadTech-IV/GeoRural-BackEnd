package br.com.fatec.georural.dto.response.pipeline;

import java.time.LocalDateTime;

// Execucao que ainda nao terminou.
public record ExecucaoEmAndamentoResponse(
        Long id,
        String etapa,
        String dagRunId,
        Long conjuntoId,
        String conjunto,
        String arquivo,
        String disparadoPor,
        LocalDateTime inicio,
        long rodandoHaSegundos,
        boolean acimaDoLimite
) {}
