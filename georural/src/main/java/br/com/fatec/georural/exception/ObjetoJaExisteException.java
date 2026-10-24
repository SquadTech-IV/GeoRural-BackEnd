package br.com.fatec.georural.exception;

/**
 * Lançada quando uma gravação é recusada porque já existe um objeto no mesmo caminho.
 * Os objetos do data lake não são sobrescritos.
 */
public class ObjetoJaExisteException extends RuntimeException {

    private final String caminho;

    public ObjetoJaExisteException(String caminho) {
        super("Já existe um objeto no caminho " + caminho + ".");
        this.caminho = caminho;
    }

    public ObjetoJaExisteException(String caminho, Throwable causa) {
        super("Já existe um objeto no caminho " + caminho + ".", causa);
        this.caminho = caminho;
    }

    public String getCaminho() {
        return caminho;
    }
}