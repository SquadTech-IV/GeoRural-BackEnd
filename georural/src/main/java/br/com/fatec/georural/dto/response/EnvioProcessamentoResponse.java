package br.com.fatec.georural.dto.response;

public record EnvioProcessamentoResponse(
        Long id,
        String nomeArquivo,
        String formato,
        Integer srid,
        String downloadUrl,
        String tabelaConteudo,
        String colunaConteudo,
        String colunaChave,
        String uriDataLake,
        String mensagem
) {}