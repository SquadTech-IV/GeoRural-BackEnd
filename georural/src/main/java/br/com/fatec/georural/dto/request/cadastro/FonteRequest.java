package br.com.fatec.georural.dto.request.cadastro;

import java.util.List;

public record FonteRequest(
    String nome,
    String sigla,
    String orgao,
    String esfera,
    String descricao,
    List<String> oferece
) {}