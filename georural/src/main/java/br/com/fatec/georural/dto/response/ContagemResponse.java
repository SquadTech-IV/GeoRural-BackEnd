package br.com.fatec.georural.dto.response;

// Contagem por rotulo, compartilhada pelas consultas de /api/v1.
public record ContagemResponse(
        String rotulo,
        long quantidade
) {}
