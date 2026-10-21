package br.com.fatec.georural.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *  Mapeia as falhas da ingestao por API para HTTP, no mesmo formato de
 * corpo do GlobalExceptionHandler do projeto ({timestamp, status, error, message, path}).
 */
@RestControllerAdvice
public class IngestaoApiExceptionHandler {

    @ExceptionHandler(FalhaComunicacaoApiException.class)
    public ResponseEntity<Map<String, Object>> falhaComunicacao(FalhaComunicacaoApiException ex,
                                                                HttpServletRequest req) {
        return corpo(HttpStatus.BAD_GATEWAY, ex.getMessage(), req);
    }

    @ExceptionHandler(DadosRecebidosInvalidosException.class)
    public ResponseEntity<Map<String, Object>> dadosInvalidos(DadosRecebidosInvalidosException ex,
                                                              HttpServletRequest req) {
        return corpo(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
    }

    private ResponseEntity<Map<String, Object>> corpo(HttpStatus status, String mensagem, HttpServletRequest req) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", LocalDateTime.now());
        corpo.put("status", status.value());
        corpo.put("error", status.getReasonPhrase());
        corpo.put("message", mensagem);
        corpo.put("path", req.getRequestURI());
        return ResponseEntity.status(status).body(corpo);
    }
}