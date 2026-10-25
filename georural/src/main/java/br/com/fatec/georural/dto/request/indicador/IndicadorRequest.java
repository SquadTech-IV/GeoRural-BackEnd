package br.com.fatec.georural.dto.request.indicador;

public record IndicadorRequest(
        String sigla,
        String nome,
        String descricao,
        String unidade
) {}