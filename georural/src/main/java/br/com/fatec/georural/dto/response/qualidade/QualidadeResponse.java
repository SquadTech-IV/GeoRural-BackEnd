package br.com.fatec.georural.dto.response.qualidade;

import java.util.List;

public record QualidadeResponse(
        Long execucaoId,
        long totalRegistros,
        long registrosValidos,
        long registrosInvalidos,
        String statusValidacao,
        long registrosEmQuarentena,
        double percentualAprovacao,
        boolean aprovado,
        List<RegraVioladaResponse> regrasVioladas,
        List<String> motivosReprovacao
) {
    public QualidadeResponse {
        regrasVioladas = regrasVioladas == null ? List.of() : List.copyOf(regrasVioladas);
        motivosReprovacao = motivosReprovacao == null ? List.of() : List.copyOf(motivosReprovacao);
    }
}