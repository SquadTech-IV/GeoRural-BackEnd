package br.com.fatec.georural.dto.response;

public record ArquivoItemResponse(
        Long id,
        String nome,
        String formato,
        Long tamanhoBytes,
        String descricao
) {}