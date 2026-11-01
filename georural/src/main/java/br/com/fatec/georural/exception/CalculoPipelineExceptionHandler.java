package br.com.fatec.georural.exception;

import br.com.fatec.georural.service.pipeline.calculo.RegraVigenteService;
import br.com.fatec.georural.service.pipeline.calculo.ResolvedorVersaoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// falhas do calculo e da publicacao em HTTP
@RestControllerAdvice
public class CalculoPipelineExceptionHandler {

    @ExceptionHandler(RegraVigenteService.RegraNaoEncontradaException.class)
    public ResponseEntity<Map<String, Object>> regraNaoEncontrada(
            RegraVigenteService.RegraNaoEncontradaException ex, HttpServletRequest req) {
        return corpo(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }



    @ExceptionHandler(ResolvedorVersaoService.VersaoNaoResolvidaException.class)
    public ResponseEntity<Map<String, Object>> versaoNaoResolvida(
            ResolvedorVersaoService.VersaoNaoResolvidaException ex, HttpServletRequest req) {
        return corpo(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
    }

    private ResponseEntity<Map<String, Object>> corpo(HttpStatus status, String mensagem,
                                                      HttpServletRequest req) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", LocalDateTime.now());
        corpo.put("status", status.value());
        corpo.put("error", status.getReasonPhrase());
        corpo.put("message", mensagem);
        corpo.put("path", req.getRequestURI());
        return ResponseEntity.status(status).body(corpo);
    }
}
