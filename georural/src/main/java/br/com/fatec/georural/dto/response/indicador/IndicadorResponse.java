package br.com.fatec.georural.dto.response.indicador;

import br.com.fatec.georural.entity.Indicador;

public record IndicadorResponse(
        Long id,
        String sigla,
        String nome,
        String descricao,
        String unidade
) {
    public static IndicadorResponse de(Indicador indicador) {
        return new IndicadorResponse(
                indicador.getId(),
                indicador.getSigla(),
                indicador.getNome(),
                indicador.getDescricao(),
                indicador.getUnidade());
    }
}