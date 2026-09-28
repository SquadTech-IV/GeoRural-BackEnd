package br.com.fatec.georural.dto.response;

public record ArquivoDownload(
        String nomeArquivo,
        String contentType,
        byte[] conteudo
) {}