package br.com.fatec.georural.exception;

/**
 * Lançada quando já existe, para o mesmo conjunto e a mesma competência, um arquivo
 * ativo com o mesmo hash. A camada de endpoint deve traduzi-la para HTTP 409.
 */
public class ArquivoDuplicadoException extends ConflitoException {

    private final Long conjuntoId;
    private final String hash;
    private final Long arquivoExistenteId;

    public ArquivoDuplicadoException(Long conjuntoId, String hash) {
        this(conjuntoId, hash, null);
    }

    public ArquivoDuplicadoException(Long conjuntoId, String hash, Long arquivoExistenteId) {
        super("Já existe um arquivo com o mesmo conteúdo (hash " + hash + ") para o conjunto "
                + conjuntoId + "."
                + (arquivoExistenteId != null ? " Arquivo existente: " + arquivoExistenteId + "." : ""));
        this.conjuntoId = conjuntoId;
        this.hash = hash;
        this.arquivoExistenteId = arquivoExistenteId;
    }

    public Long getConjuntoId() {
        return conjuntoId;
    }

    public String getHash() {
        return hash;
    }

    public Long getArquivoExistenteId() {
        return arquivoExistenteId;
    }
}
