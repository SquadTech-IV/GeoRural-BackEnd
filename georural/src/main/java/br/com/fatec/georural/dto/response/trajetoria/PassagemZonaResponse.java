package br.com.fatec.georural.dto.response.trajetoria;

import java.time.LocalDateTime;

public record PassagemZonaResponse(
        String zona,
        String status,
        LocalDateTime quando,
        String contagem,
        String observacao
) {}
