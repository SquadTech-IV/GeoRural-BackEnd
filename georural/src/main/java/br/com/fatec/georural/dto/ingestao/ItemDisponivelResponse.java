package br.com.fatec.georural.dto.ingestao;

public record ItemDisponivelResponse(
        String identificador,
        String nome,
        String descricao,
        boolean jaRecebido
) {
}