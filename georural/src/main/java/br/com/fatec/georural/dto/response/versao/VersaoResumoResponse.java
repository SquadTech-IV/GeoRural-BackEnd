package br.com.fatec.georural.dto.response.versao;

import java.time.LocalDateTime;
import java.util.List;

/** Resumo de uma versao para a fila de conferencia/publicacao */
public record VersaoResumoResponse(
        Long versaoId,
        Long numero,
        String situacao,
        Long conjuntoId,
        String conjuntoNome,
        List<IndicadorResumoResponse> indicadores,
        List<String> regrasCalculo,
        String competencia,
        Long execucaoId,
        LocalDateTime dataPublicacao,
        String responsavel,
        Long versaoVigenteAtual,
        LocalDateTime vigenteDesde
) {
    public VersaoResumoResponse {
        indicadores = indicadores == null ? List.of() : List.copyOf(indicadores);
        regrasCalculo = regrasCalculo == null ? List.of() : List.copyOf(regrasCalculo);
    }
}