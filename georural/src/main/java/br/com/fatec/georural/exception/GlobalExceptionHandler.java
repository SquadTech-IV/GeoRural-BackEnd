package br.com.fatec.georural.exception;

import br.com.fatec.georural.dto.response.ErroResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.stream.Collectors;
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

    @ExceptionHandler(RequisicaoInvalidaException.class)
    public ResponseEntity<ErroResponse> tratarRequisicaoInvalida(
            RequisicaoInvalidaException ex, HttpServletRequest req) {
        return montar(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler(EstadoInconsistenteException.class)
    public ResponseEntity<ErroResponse> tratarEstadoInconsistente(
            EstadoInconsistenteException ex, HttpServletRequest req) {
        return montar(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> tratarTipoInvalido(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String mensagem = String.format("Parâmetro '%s' deve ser um número válido.", ex.getName());
        return montar(HttpStatus.BAD_REQUEST, mensagem, req);
    }

    // erro de seguranca: qualquer erro nao previsto vira 500 padronizado
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> tratarGenerico(
            Exception ex, HttpServletRequest req) {
        // imprime o erro completo no console para diagnostico
        ex.printStackTrace();
        return montar(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno no servidor.", req);
    }

        @ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErroResponse> tratarCamposInvalidos(
    MethodArgumentNotValidException erro,
    HttpServletRequest request
) {
    String mensagem = erro.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(campo ->
            campo.getField() + ": " +
            campo.getDefaultMessage()
        )
        .distinct()
        .sorted()
        .collect(Collectors.joining("; "));

    if (mensagem.isBlank()) {
        mensagem = "Confira os campos informados.";
    }

    return montar(
        HttpStatus.BAD_REQUEST,
        mensagem,
        request
    );
}

@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<ErroResponse> tratarJsonInvalido(
    HttpMessageNotReadableException erro,
    HttpServletRequest request
) {
    return montar(
        HttpStatus.BAD_REQUEST,
        "O corpo da requisição está ausente ou contém " +
            "um JSON inválido. Confira os campos e seus tipos.",
        request
    );
}

@ExceptionHandler(MissingServletRequestParameterException.class)
public ResponseEntity<ErroResponse> tratarParametroAusente(
    MissingServletRequestParameterException erro,
    HttpServletRequest request
) {
    return montar(
        HttpStatus.BAD_REQUEST,
        "O parâmetro '" + erro.getParameterName() +
            "' é obrigatório.",
        request
    );
}

@ExceptionHandler(MissingServletRequestPartException.class)
public ResponseEntity<ErroResponse> tratarParteAusente(
    MissingServletRequestPartException erro,
    HttpServletRequest request
) {
    return montar(
        HttpStatus.BAD_REQUEST,
        "O campo de upload '" +
            erro.getRequestPartName() +
            "' é obrigatório.",
        request
    );
}

@ExceptionHandler(MaxUploadSizeExceededException.class)
public ResponseEntity<ErroResponse> tratarUploadMuitoGrande(
    MaxUploadSizeExceededException erro,
    HttpServletRequest request
) {
    return montar(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "O upload excedeu o tamanho máximo permitido.",
        request
    );
}

@ExceptionHandler(MultipartException.class)
public ResponseEntity<ErroResponse> tratarUploadInvalido(
    MultipartException erro,
    HttpServletRequest request
) {
    return montar(
        HttpStatus.BAD_REQUEST,
        "Não foi possível interpretar o upload. " +
            "Envie os arquivos usando multipart/form-data.",
        request
    );
}

@ExceptionHandler(DataIntegrityViolationException.class)
public ResponseEntity<ErroResponse> tratarIntegridade(
    DataIntegrityViolationException erro,
    HttpServletRequest request
) {
    return montar(
        HttpStatus.CONFLICT,
        "Não foi possível salvar o cadastro. " +
            "Verifique se existem dados duplicados " +
            "ou associações incompatíveis.",
        request
    );
}

@ExceptionHandler({
    PessimisticLockingFailureException.class,
    ObjectOptimisticLockingFailureException.class
})
public ResponseEntity<ErroResponse> tratarConcorrencia(
    Exception erro,
    HttpServletRequest request
) {
    return montar(
        HttpStatus.CONFLICT,
        "O registro foi alterado ou está sendo utilizado " +
            "por outra operação. Recarregue os dados " +
            "e tente novamente.",
        request
    );
}

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResponse> tratarArquivoGrande(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return montar(HttpStatus.CONTENT_TOO_LARGE, "O arquivo excede o tamanho máximo permitido.", req);
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