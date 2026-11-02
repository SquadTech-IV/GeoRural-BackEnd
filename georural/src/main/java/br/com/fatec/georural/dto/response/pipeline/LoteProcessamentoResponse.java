package br.com.fatec.georural.dto.response.pipeline;

import java.util.List;

// resultado de um lote: uma linha por arquivo
public record LoteProcessamentoResponse(
        int arquivos,
        int concluidos,
        int falhados,
        long feicoesRecebidas,
        long feicoesRejeitadas,
        String mensagem,
        List<ArquivoDoLoteResponse> detalhes) {

    public record ArquivoDoLoteResponse(
            Long arquivoBrutoId,
            String nome,
            String dagRunId,
            String situacao,
            long recebidas,
            long aprovadas,
            long rejeitadas,
            Long versaoGerada,
            String mensagem,
            List<EtapaExecutadaResponse> etapas) {
    }
}
