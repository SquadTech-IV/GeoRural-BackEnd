package br.com.fatec.georural.dto.response.catalogo;

import br.com.fatec.georural.dto.response.PaginaResponse;

// Historico de versoes de um conjunto.
public record HistoricoVersoesResponse(
        ConjuntoIdentificacao conjunto,
        Long versaoVigenteId,
        PaginaResponse<VersaoDetalheResponse> versoes
) {}
