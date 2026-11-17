package br.com.fatec.georural.service.ingestao;

// desfecho de um arquivo dentro de um envio
public enum SituacaoEnvio {

    ACEITO("Arquivo recebido na zona bruta."),
    DUPLICADO("Este conteudo ja esta no acervo nesta competencia."),
    FORMATO_RECUSADO("Formato nao aceito."),
    ARQUIVO_VAZIO("O arquivo chegou vazio."),
    RECUSADO("Recusado por regra de cadastro."),
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

    // pede acao de quem enviou; duplicado nao pede
    public boolean exigeAtencao() {
        return this != ACEITO && this != DUPLICADO;
    }
}
