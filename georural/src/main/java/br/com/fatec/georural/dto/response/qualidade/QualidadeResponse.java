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
        List<RegraAvaliadaResponse> regras,
        List<String> motivosReprovacao
) {
    public QualidadeResponse {
        regras = regras == null ? List.of() : List.copyOf(regras);
        motivosReprovacao = motivosReprovacao == null ? List.of() : List.copyOf(motivosReprovacao);
    }
}