package br.com.fatec.georural.dto.response.catalogo;

// Descricao de um campo publicado pela consulta de dados.
public record CampoResponse(
        String nome,
        String tipo,
        String coluna
) {}
