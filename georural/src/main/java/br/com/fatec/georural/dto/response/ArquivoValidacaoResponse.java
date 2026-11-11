package br.com.fatec.georural.dto.response;

import br.com.fatec.georural.service.ingestao.SituacaoEnvio;

/**
 * Resultado de UM arquivo dentro de um envio.
 */
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
        Long arquivoExistenteId,
        /**
         * Competencia com que o arquivo entrou -- parte da identidade dele, junto com o
         * conjunto e o hash. Vem preenchida quando se sabe: a informada na chamada (em
         * todos os itens) ou a que ficou gravada no registro (nos aceitos). Nula quando o
         * arquivo foi recusado antes de ter competencia resolvida.
         */
        String competencia
) {

    public ArquivoValidacaoResponse(
            String nomeArquivo,
            String extensao,
            String mimeDetectado,
            boolean aceito
    ) {
        this(nomeArquivo, extensao, mimeDetectado, aceito, null, null, null,
                aceito ? SituacaoEnvio.ACEITO : SituacaoEnvio.RECUSADO, null, null, null);
    }

    public static ArquivoValidacaoResponse aceito(String nome, String extensao, String mime,
                                                  Long arquivoId, Long fonteId, Long conjuntoId,
                                                  String competencia) {
        return new ArquivoValidacaoResponse(nome, extensao, mime, true, arquivoId, fonteId,
                conjuntoId, SituacaoEnvio.ACEITO, SituacaoEnvio.ACEITO.descricaoPadrao(), null,
                competencia);
    }

    public static ArquivoValidacaoResponse duplicado(String nome, String extensao, String mime,
                                                     Long fonteId, Long conjuntoId,
                                                     Long arquivoExistenteId, String motivo,
                                                     String competencia) {
        return new ArquivoValidacaoResponse(nome, extensao, mime, false, null, fonteId,
                conjuntoId, SituacaoEnvio.DUPLICADO,
                motivo != null ? motivo : SituacaoEnvio.DUPLICADO.descricaoPadrao(),
                arquivoExistenteId, competencia);
    }

    public static ArquivoValidacaoResponse recusado(String nome, String extensao, String mime,
                                                    Long fonteId, Long conjuntoId,
                                                    SituacaoEnvio situacao, String motivo,
                                                    String competencia) {
        return new ArquivoValidacaoResponse(nome, extensao, mime, false, null, fonteId,
                conjuntoId, situacao,
                motivo != null && !motivo.isBlank() ? motivo : situacao.descricaoPadrao(), null,
                competencia);
    }
}
