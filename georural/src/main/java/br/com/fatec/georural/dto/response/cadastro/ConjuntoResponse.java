package br.com.fatec.georural.dto.response.cadastro;

import java.time.LocalDateTime;

public record ConjuntoResponse(
    Long id,
    Long fonteId,
    String fonteNome,
    String nome,
    String tipoCamada,
    String descricao,
    String competencia,
    String situacao,
    boolean ativo,
    LocalDateTime dataCadastro
) {}