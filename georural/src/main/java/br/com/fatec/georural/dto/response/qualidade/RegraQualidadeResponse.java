package br.com.fatec.georural.dto.response.qualidade;

// Uma regra do catalogo de qualidade.
public record RegraQualidadeResponse(
        Long id,
        String nome,
        String tipo,
        String descricao,
        String severidade,
        boolean ativa,
        long violacoes
) {}
