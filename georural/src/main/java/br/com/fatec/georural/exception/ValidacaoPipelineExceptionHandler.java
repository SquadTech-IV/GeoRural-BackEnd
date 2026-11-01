package br.com.fatec.georural.exception;

import br.com.fatec.georural.service.pipeline.leitor.LeitorFactory;
import br.com.fatec.georural.service.pipeline.perfil.RegistroPerfis;
import br.com.fatec.georural.service.pipeline.validacao.ReprojetorFeicao;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// falhas da validacao em HTTP, no mesmo corpo do GlobalExceptionHandler
@RestControllerAdvice
public class ValidacaoPipelineExceptionHandler {

    @ExceptionHandler(RegistroPerfis.PerfilNaoDefinidoException.class)
    public ResponseEntity<Map<String, Object>> perfilNaoDefinido(
            RegistroPerfis.PerfilNaoDefinidoException ex, HttpServletRequest req) {
        return corpo(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
    }

    @ExceptionHandler(LeitorFactory.FormatoSemLeitorException.class)
    public ResponseEntity<Map<String, Object>> formatoSemLeitor(
            LeitorFactory.FormatoSemLeitorException ex, HttpServletRequest req) {
        return corpo(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
    }

    @ExceptionHandler(ReprojetorFeicao.ReprojecaoFalhouException.class)
    public ResponseEntity<Map<String, Object>> reprojecaoFalhou(
            ReprojetorFeicao.ReprojecaoFalhouException ex, HttpServletRequest req) {
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
