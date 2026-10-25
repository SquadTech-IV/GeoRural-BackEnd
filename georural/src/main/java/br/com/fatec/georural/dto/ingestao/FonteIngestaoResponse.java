package br.com.fatec.georural.dto.ingestao;

import java.util.List;

public record FonteIngestaoResponse(
        Long id,
        String nome,
        String sigla,
        List<ConjuntoIngestaoResponse> conjuntos
) {
}