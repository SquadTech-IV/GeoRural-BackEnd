package br.com.fatec.georural.service.ingestao;

public enum SituacaoEnvio {

    /** Recebido e registrado na zona bruta. */
    ACEITO("Arquivo recebido na zona bruta."),

    /** Mesmo conjunto, mesma competencia e mesmo conteudo: ja esta no acervo. */
    DUPLICADO("Este conteudo ja esta no acervo nesta competencia."),

    /** Extensao ou assinatura de conteudo fora do que o projeto le. */
    FORMATO_RECUSADO("Formato nao aceito."),

    /** Chegou sem bytes. Nao interrompe o resto do lote. */
    ARQUIVO_VAZIO("O arquivo chegou vazio."),

    /** Regra de negocio barrou: conjunto arquivado, responsavel inexistente, etc. */
    RECUSADO("Recusado por regra de cadastro."),

    /** Falha inesperada (armazenamento, leitura, banco). Vale tentar de novo. */
    ERRO("Falha ao receber o arquivo.");

    private final String descricaoPadrao;

    SituacaoEnvio(String descricaoPadrao) {
        this.descricaoPadrao = descricaoPadrao;
    }

    public String descricaoPadrao() {
        return descricaoPadrao;
    }

    public boolean entrou() {
        return this == ACEITO;
    }

    /** Duplicado nao e falha: o conteudo esta no acervo, so nao entrou de novo. */
    public boolean exigeAtencao() {
        return this != ACEITO && this != DUPLICADO;
    }
}
