package br.com.fatec.georural.dto.response.linhagem;

// Parametro com o valor que a regra tinha no momento do calculo.
public record ParametroLinhagemResponse(
        String nome,
        String rotulo,
        String valor,
        String unidade
) {}
