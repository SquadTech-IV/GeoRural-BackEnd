package br.com.fatec.georural.dto.response;

public record ArquivoValidacaoResponse(
    String nomeArquivo,
    String extensao,
    String mimeDetectado,
    boolean aceito,
    Long arquivoId,
    Long fonteId,
    Long conjuntoId
) {
    public ArquivoValidacaoResponse(
        String nomeArquivo,
        String extensao,
        String mimeDetectado,
        boolean aceito
    ) {
        this(
            nomeArquivo,
            extensao,
            mimeDetectado,
            aceito,
            null,
            null,
            null
        );
    }
}