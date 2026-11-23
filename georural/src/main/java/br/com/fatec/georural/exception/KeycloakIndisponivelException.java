package br.com.fatec.georural.exception;

public class KeycloakIndisponivelException extends RuntimeException {

    public KeycloakIndisponivelException(String message) {
        super(message);
    }

    public KeycloakIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}