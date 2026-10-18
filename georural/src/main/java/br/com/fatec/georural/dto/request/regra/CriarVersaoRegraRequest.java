package br.com.fatec.georural.dto.request.regra;

import java.util.List;

public record CriarVersaoRegraRequest(
        String motivo,
        String autor,
        String formula,
        String descricao,
        List<ParametroInput> parametros
) {
}