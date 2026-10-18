package br.com.fatec.georural.dto.ingestao;

public record ResultadoImportacaoItem(
        String identificador,
        String nome,
        String status,
        String mensagem,
        Long quantidadeRegistros,
        Long arquivoBrutoId
) {
    public static ResultadoImportacaoItem sucesso(String identificador, String nome, long quantidade, Long arquivoBrutoId) {
        return new ResultadoImportacaoItem(identificador, nome, "SUCESSO",
                "Importado para a zona bruta.", quantidade, arquivoBrutoId);
    }

    public static ResultadoImportacaoItem jaRecebido(String identificador, String nome) {
        return new ResultadoImportacaoItem(identificador, nome, "JA_RECEBIDO",
                "Ja estava no datalake; ignorado.", null, null);
    }

    public static ResultadoImportacaoItem falha(String identificador, String nome, String motivo) {
        return new ResultadoImportacaoItem(identificador, nome, "FALHA", motivo, null, null);
    }
}