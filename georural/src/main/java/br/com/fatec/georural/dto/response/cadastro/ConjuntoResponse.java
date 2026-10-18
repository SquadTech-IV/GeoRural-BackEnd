package br.com.fatec.georural.dto.response.cadastro;

import java.time.LocalDateTime;

public record ConjuntoResponse(
    Long id,
    Long fonteId,
    String fonteNome,
    String nome,
    String tipoCamada,
    String descricao,
    String esquemaTecnico,
    Integer srs,
    String competencia,
    String cobertura,
    String periodicidade,
    String situacao,
    boolean ativo,
    LocalDateTime dataCadastro
) {}