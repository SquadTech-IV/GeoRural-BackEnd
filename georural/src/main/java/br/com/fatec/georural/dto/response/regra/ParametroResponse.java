package br.com.fatec.georural.dto.response.regra;

import br.com.fatec.georural.entity.ParametroRegraCalculo;

public record ParametroResponse(
        String nome,
        String tipo,
        String unidade,
        String valor
) {
    public static ParametroResponse de(ParametroRegraCalculo parametro) {
        return new ParametroResponse(
                parametro.getNome(),
                parametro.getTipo(),
                parametro.getUnidade(),
                parametro.getValor());
    }
}