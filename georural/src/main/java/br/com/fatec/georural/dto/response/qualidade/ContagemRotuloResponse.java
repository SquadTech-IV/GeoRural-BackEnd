package br.com.fatec.georural.dto.response.qualidade;

// Contagem simples por rotulo, usada nas quebras por etapa e por situacao.
public record ContagemRotuloResponse(
        String rotulo,
        long quantidade
) {}
