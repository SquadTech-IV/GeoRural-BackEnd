package br.com.fatec.georural.dto.response.qualidade;

// Quantas vezes uma regra de qualidade foi violada.
public record ViolacaoRegraResponse(
        Long regraId,
        String regra,
        String tipo,
        String severidade,
        long quantidade
) {}
