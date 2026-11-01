package br.com.fatec.georural.dto.response.pipeline;

/** Catalogo das etapas: quais existem e quais ja estao implementadas. */
public record EtapaDisponivelResponse(
        String etapa,
        int ordem,
        String descricao,
        boolean automatica,
        boolean implementada) {
}
