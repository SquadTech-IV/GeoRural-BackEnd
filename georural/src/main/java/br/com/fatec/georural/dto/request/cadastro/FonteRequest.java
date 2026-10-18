package br.com.fatec.georural.dto.request.cadastro;

public record FonteRequest(
    String nome,
    String sigla,
    String orgao,
    String esfera,
    String descricao
) {}