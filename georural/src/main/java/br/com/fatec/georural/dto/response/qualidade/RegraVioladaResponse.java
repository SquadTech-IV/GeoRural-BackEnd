package br.com.fatec.georural.dto.response.qualidade;

public record RegraVioladaResponse(
        String nomeRegra,
        String tipo,
        String severidade,
        long quantidade
) {
}