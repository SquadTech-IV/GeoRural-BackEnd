package br.com.fatec.georural.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum SituacaoVersao {

    RASCUNHO,
    VIGENTE,
    ANTERIOR;

    private static final Map<SituacaoVersao, Set<SituacaoVersao>> TRANSICOES = Map.of(
            RASCUNHO, EnumSet.of(VIGENTE),
            VIGENTE,  EnumSet.of(ANTERIOR),
            ANTERIOR, EnumSet.noneOf(SituacaoVersao.class)
    );

    /** Indica se a transicao deste estado para {@code destino} e permitida. */
    public boolean podeTransicionarPara(SituacaoVersao destino) {
        return destino != null && TRANSICOES.get(this).contains(destino);
    }

    /** Versao ja publicada (VIGENTE ou ANTERIOR): conteudo imutavel. */
    public boolean ehImutavel() {
        return this == VIGENTE || this == ANTERIOR;
    }

    /** Versao ainda em rascunho: pode ser alterada e e a unica publicavel. */
    public boolean ehRascunho() {
        return this == RASCUNHO;
    }

    /** Converte o valor textual persistido (ver_situacao) para o enum. */
    public static SituacaoVersao de(String valor) {
        if (valor == null) {
            throw new IllegalArgumentException("Situacao de versao nao informada");
        }
        try {
            return SituacaoVersao.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Situacao de versao invalida: " + valor);
        }
    }
}