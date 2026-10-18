package br.com.fatec.georural.dto.request.cadastro;

public record ConjuntoRequest(
    Long fonteId,
    String nome,
    String tipoCamada,
    String descricao,
    String esquemaTecnico,
    Integer srs,
    String competencia,
    String cobertura,
    String periodicidade
) {}