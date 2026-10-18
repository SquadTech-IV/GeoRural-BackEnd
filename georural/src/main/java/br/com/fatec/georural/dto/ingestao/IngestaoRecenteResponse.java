package br.com.fatec.georural.dto.ingestao;

import java.time.LocalDateTime;

public record IngestaoRecenteResponse(
        Long id,
        String nome,
        String formato,
        Long tamanhoBytes,
        LocalDateTime quando,
        String status,
        String zonaAtual,
        Long quantidadeRegistros
) {
}