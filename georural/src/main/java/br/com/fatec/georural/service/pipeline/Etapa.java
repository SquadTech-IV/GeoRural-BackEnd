package br.com.fatec.georural.service.pipeline;

public enum Etapa {

    INGESTAO(1, "Recebimento na zona bruta"),
    VALIDACAO(2, "Validacao e quarentena"),
    TRATAMENTO(3, "Tratamento e padronizacao"),
    CALCULO(4, "Cruzamento geoespacial e calculo"),
    QUALIDADE(5, "Qualidade e versionamento"),
    PUBLICACAO(6, "Publicacao da versao");

    private final int ordem;
    private final String descricao;

    Etapa(int ordem, String descricao) {
        this.ordem = ordem;
        this.descricao = descricao;
    }

    public int ordem() {
        return ordem;
    }

    public String descricao() {
        return descricao;
    }

    /** Etapas que o pipeline executa automaticamente (1 a 5).*/
    public boolean automatica() {
        return this != PUBLICACAO;
    }
}
