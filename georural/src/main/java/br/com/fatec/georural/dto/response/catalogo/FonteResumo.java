package br.com.fatec.georural.dto.response.catalogo;

// Fonte do dado, como aparece no catalogo.
public record FonteResumo(
        Long id,
        String nome,
        String sigla,
        String esfera,
        boolean ativa
) {}
