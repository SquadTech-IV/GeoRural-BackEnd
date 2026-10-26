package br.com.fatec.georural.dto.request.regra;

public record ParametroInput(
        String nome,
        String tipo,
        String unidade,
        String valor
) {
}