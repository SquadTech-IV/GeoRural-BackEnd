package br.com.fatec.georural.dto.ingestao;

/**
 * Notificacao pronta para a tela exibir.
 * notify() da tela: "success" | "error" | "warning" | "info".
 */
public record NotificacaoIngestao(String tipo, String titulo, String mensagem) {

    public static NotificacaoIngestao sucesso(String titulo, String mensagem) {
        return new NotificacaoIngestao("success", titulo, mensagem);
    }

    public static NotificacaoIngestao erro(String titulo, String mensagem) {
        return new NotificacaoIngestao("error", titulo, mensagem);
    }

    public static NotificacaoIngestao aviso(String titulo, String mensagem) {
        return new NotificacaoIngestao("warning", titulo, mensagem);
    }

    public static NotificacaoIngestao info(String titulo, String mensagem) {
        return new NotificacaoIngestao("info", titulo, mensagem);
    }
}