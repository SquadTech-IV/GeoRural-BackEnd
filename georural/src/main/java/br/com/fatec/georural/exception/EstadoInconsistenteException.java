package br.com.fatec.georural.exception;

public class EstadoInconsistenteException extends RuntimeException {
    public EstadoInconsistenteException(String mensagem) {
        super(mensagem);
    }
}