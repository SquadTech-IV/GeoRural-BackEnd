package br.com.fatec.georural.dto.response.catalogo;

import br.com.fatec.georural.dto.response.PaginaResponse;

import java.util.List;
import java.util.Map;

// Registros tratados de um conjunto, com a descricao dos campos devolvidos.
public record DadosConjuntoResponse(
        ConjuntoIdentificacao conjunto,
        VersaoResumo versao,
        boolean versionada,
        List<CampoResponse> campos,
        String campoGeometria,
        PaginaResponse<Map<String, Object>> registros
) {}
