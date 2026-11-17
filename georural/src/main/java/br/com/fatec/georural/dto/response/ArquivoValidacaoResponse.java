package br.com.fatec.georural.dto.response;

import br.com.fatec.georural.service.ingestao.SituacaoEnvio;

// resultado de um arquivo dentro de um envio.
// aceito continua no contrato porque o front atual decide por ele.
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
        // quando situacao = DUPLICADO: o arquivo que ja estava no acervo
        Long arquivoExistenteId,
        // competencia com que o arquivo entrou; nula quando nao chegou a ser resolvida
        String competencia,
        long tamanhoBytes
) {

    public ArquivoValidacaoResponse(
            String nomeArquivo,
            String extensao,
            String mimeDetectado,
            boolean aceito
    ) {
        this(nomeArquivo, extensao, mimeDetectado, aceito, null, null, null,
                aceito ? SituacaoEnvio.ACEITO : SituacaoEnvio.RECUSADO, null, null, null, 0L);
    }

    public static ArquivoValidacaoResponse aceito(String nome, String extensao, String mime,
                                                  Long arquivoId, Long fonteId, Long conjuntoId,
                                                  String competencia, long tamanhoBytes) {
        return new ArquivoValidacaoResponse(nome, extensao, mime, true, arquivoId, fonteId,
                conjuntoId, SituacaoEnvio.ACEITO, SituacaoEnvio.ACEITO.descricaoPadrao(), null,
                competencia, tamanhoBytes);
    }

    public static ArquivoValidacaoResponse duplicado(String nome, String extensao, String mime,
                                                     Long fonteId, Long conjuntoId,
                                                     Long arquivoExistenteId, String motivo,
                                                     String competencia, long tamanhoBytes) {
        return new ArquivoValidacaoResponse(nome, extensao, mime, false, null, fonteId,
                conjuntoId, SituacaoEnvio.DUPLICADO,
                motivo != null ? motivo : SituacaoEnvio.DUPLICADO.descricaoPadrao(),
                arquivoExistenteId, competencia, tamanhoBytes);
    }

    public static ArquivoValidacaoResponse recusado(String nome, String extensao, String mime,
                                                    Long fonteId, Long conjuntoId,
                                                    SituacaoEnvio situacao, String motivo,
                                                    String competencia, long tamanhoBytes) {
        return new ArquivoValidacaoResponse(nome, extensao, mime, false, null, fonteId,
                conjuntoId, situacao,
                motivo != null && !motivo.isBlank() ? motivo : situacao.descricaoPadrao(), null,
                competencia, tamanhoBytes);
    }
}
