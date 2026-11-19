package br.com.fatec.georural.security;

import br.com.fatec.georural.dto.response.ErroResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class SegurancaExceptionHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final AuditoriaAcessoNegadoService auditoriaAcessoNegadoService;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        log.warn("401 {} {} -> {}", request.getMethod(), request.getRequestURI(), authException.getMessage());
        escreverErro(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "Token de autenticação ausente, expirado ou inválido."
        );
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.warn("403 {} {} -> {}", request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());

        auditoriaAcessoNegadoService.registrarAcessoNegado(
                request,
                auth,
                accessDeniedException.getMessage()
        );

        escreverErro(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "Acesso negado: você não possui permissão para executar esta operação."
        );
    }

    private void escreverErro(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String mensagem) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        ErroResponse corpo = new ErroResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                request.getRequestURI()
        );

        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), corpo);
    }
}