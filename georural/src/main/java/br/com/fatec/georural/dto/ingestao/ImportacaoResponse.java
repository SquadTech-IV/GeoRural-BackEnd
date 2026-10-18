package br.com.fatec.georural.dto.ingestao;

import java.util.List;

public record ImportacaoResponse(
        int total,
        int importados,
        int jaRecebidos,
        int falhas,
        List<ResultadoImportacaoItem> itens,
        List<NotificacaoIngestao> notificacoes
) {
    public static ImportacaoResponse de(List<ResultadoImportacaoItem> itens) {
        int imp = 0, ja = 0, fal = 0;
        for (ResultadoImportacaoItem i : itens) {
            switch (i.status()) {
                case "SUCESSO" -> imp++;
                case "JA_RECEBIDO" -> ja++;
                default -> fal++;
            }
        }
        List<NotificacaoIngestao> notas = NotificacaoIngestaoFactory.construir(imp, ja, itens);
        return new ImportacaoResponse(itens.size(), imp, ja, fal, List.copyOf(itens), notas);
    }
}