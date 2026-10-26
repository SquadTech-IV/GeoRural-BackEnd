package br.com.fatec.georural.exception;

/**
 * Lançada quando já existe, para o mesmo conjunto, um arquivo ativo (situação diferente de FALHA)
 * com o mesmo hash. A camada de endpoint deve traduzi-la para HTTP 409.
 */
public class ArquivoDuplicadoException extends ConflitoException {

    private final Long conjuntoId;
    private final String hash;

    public ArquivoDuplicadoException(Long conjuntoId, String hash) {
        super("Já existe um arquivo com o mesmo conteúdo (hash " + hash + ") para o conjunto " + conjuntoId + ".");
        this.conjuntoId = conjuntoId;
        this.hash = hash;
    }

    public Long getConjuntoId() {
        return conjuntoId;
    }

    public String getHash() {
        return hash;
    }
}
