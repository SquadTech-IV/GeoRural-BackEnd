package br.com.fatec.georural.dto.response.catalogo;

import java.util.List;

// Tipo de camada do conjunto e os formatos que ele aceita.
public record TipoResumo(
        String codigo,
        String nome,
        List<String> formatos
) {}
