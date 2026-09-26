package br.com.fatec.georural.dto.response;

public record ArquivoValidacaoResponse(
        String nomeArquivo,
        String extensao,
        String mimeDetectado,
        boolean aceito
) {}