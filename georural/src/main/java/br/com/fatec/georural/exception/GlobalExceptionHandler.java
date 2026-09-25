package br.com.fatec.georural.exception;

import br.com.fatec.georural.dto.response.ErroResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> tratarNaoEncontrado(
            RecursoNaoEncontradoException ex, HttpServletRequest req) {
        return montar(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponse> tratarConflito(
            ConflitoException ex, HttpServletRequest req) {
        return montar(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    // rede de seguranca: qualquer erro nao previsto vira 500 padronizado
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> tratarGenerico(
            Exception ex, HttpServletRequest req) {
        return montar(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno no servidor.", req);
    }

    private ResponseEntity<ErroResponse> montar(
            HttpStatus status, String mensagem, HttpServletRequest req) {
        ErroResponse corpo = new ErroResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                req.getRequestURI());
        return ResponseEntity.status(status).body(corpo);
    }
}