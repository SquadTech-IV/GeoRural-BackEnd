package br.com.fatec.georural.dto.response;

import br.com.fatec.georural.service.ingestao.SituacaoEnvio;

public record ArquivoValidacaoResponse(
        String nomeArquivo,
        String extensao,
        String mimeDetectado,
        boolean aceito,
        Long arquivoId,
        Long fonteId,
        Long conjuntoId,
        SituacaoEnvio situacao,
        String motivo,
        /** Quando situacao = DUPLICADO: o arquivo que ja estava no acervo. */
        Long arquivoExistenteId
) {

    public ArquivoValidacaoResponse(
            String nomeArquivo,
            String extensao,
            String mimeDetectado,
            boolean aceito
    ) {
        this(nomeArquivo, extensao, mimeDetectado, aceito, null, null, null,
                aceito ? SituacaoEnvio.ACEITO : SituacaoEnvio.RECUSADO, null, null);
    }

    public static ArquivoValidacaoResponse aceito(String nome, String extensao, String mime,
                                                  Long arquivoId, Long fonteId, Long conjuntoId) {
        return new ArquivoValidacaoResponse(nome, extensao, mime, true, arquivoId, fonteId,
                conjuntoId, SituacaoEnvio.ACEITO, SituacaoEnvio.ACEITO.descricaoPadrao(), null);
    }

    public static ArquivoValidacaoResponse duplicado(String nome, String extensao, String mime,
                                                     Long fonteId, Long conjuntoId,
                                                     Long arquivoExistenteId, String motivo) {
        return new ArquivoValidacaoResponse(nome, extensao, mime, false, null, fonteId,
                conjuntoId, SituacaoEnvio.DUPLICADO,
                motivo != null ? motivo : SituacaoEnvio.DUPLICADO.descricaoPadrao(),
                arquivoExistenteId);
    }

    public static ArquivoValidacaoResponse recusado(String nome, String extensao, String mime,
                                                    Long fonteId, Long conjuntoId,
                                                    SituacaoEnvio situacao, String motivo) {
        return new ArquivoValidacaoResponse(nome, extensao, mime, false, null, fonteId,
                conjuntoId, situacao,
                motivo != null && !motivo.isBlank() ? motivo : situacao.descricaoPadrao(), null);
    }
}
