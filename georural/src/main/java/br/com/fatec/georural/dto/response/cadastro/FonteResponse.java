package br.com.fatec.georural.dto.response.cadastro;

import java.time.LocalDateTime;
import java.util.List;

public record FonteResponse(
    Long id,
    String nome,
    String sigla,
    String orgao,
    String esfera,
    String descricao,
    boolean ativo,
    LocalDateTime dataCadastro,
    List<String> oferece
) {}