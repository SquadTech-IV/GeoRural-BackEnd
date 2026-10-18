package br.com.fatec.georural.exception;

public class FalhaComunicacaoApiException extends RuntimeException {

    public FalhaComunicacaoApiException(String message) {
        super(message);
    }

    public FalhaComunicacaoApiException(String message, Throwable cause) {
        super(message, cause);
    }
}