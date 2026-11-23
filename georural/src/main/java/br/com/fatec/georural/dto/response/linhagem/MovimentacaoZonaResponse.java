package br.com.fatec.georural.dto.response.linhagem;

import java.time.LocalDateTime;

// Passagem de um arquivo por uma zona do data lake.
public record MovimentacaoZonaResponse(
        String zona,
        String status,
        String observacao,
        Long execucaoId,
        LocalDateTime data
) {}
