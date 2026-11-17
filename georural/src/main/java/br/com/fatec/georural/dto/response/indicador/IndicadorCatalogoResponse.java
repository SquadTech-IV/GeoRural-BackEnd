package br.com.fatec.georural.dto.response.indicador;

// Um indicador no catalogo da API, com a regra que esta valendo.
public record IndicadorCatalogoResponse(
        Long id,
        String sigla,
        String nome,
        String descricao,
        String unidade,
        RegraVigenteResponse regraVigente,
        long resultados
) {}
