package br.com.fatec.georural.dto.ingestao;

import java.util.ArrayList;
import java.util.List;


public final class NotificacaoIngestaoFactory {

    private NotificacaoIngestaoFactory() {
    }

    public static List<NotificacaoIngestao> construir(int importados, int jaRecebidos,
                                                      List<ResultadoImportacaoItem> itens) {
        List<NotificacaoIngestao> notas = new ArrayList<>();

        if (importados > 0) {
            notas.add(NotificacaoIngestao.sucesso("Importação concluída",
                    importados + " arquivo(s) enviado(s) à zona bruta."));
        }
        if (jaRecebidos > 0) {
            notas.add(NotificacaoIngestao.info("Nada novo",
                    jaRecebidos + " item(ns) já estavam no datalake e foram ignorados."));
        }
        for (ResultadoImportacaoItem item : itens) {
            if ("FALHA".equals(item.status())) {
                notas.add(NotificacaoIngestao.erro("Falha em " + item.nome(), item.mensagem()));
            }
        }
        if (notas.isEmpty()) {
            notas.add(NotificacaoIngestao.info("Importação", "Nada a importar."));
        }
        return notas;
    }
}