package br.com.fatec.georural.dto.response.catalogo;

// Identificacao minima do conjunto, repetida nas consultas de dados e versoes.
public record ConjuntoIdentificacao(
        Long id,
        String nome,
        String tipoCamada,
        String tabela,
        Integer srs
) {}
