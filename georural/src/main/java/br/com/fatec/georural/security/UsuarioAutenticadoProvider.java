package br.com.fatec.georural.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Component
public class UsuarioAutenticadoProvider {

    public static final String REQUEST_ATTR = UsuarioAutenticado.class.getName();

    public Optional<UsuarioAutenticado> obterOpcional() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            Object valor = request.getAttribute(REQUEST_ATTR);
            if (valor instanceof UsuarioAutenticado usuario) {
                return Optional.of(usuario);
            }
        }
        return Optional.empty();
    }

    public UsuarioAutenticado obterObrigatorio() {
        return obterOpcional().orElseThrow(() ->
                new AuthenticationCredentialsNotFoundException("Usuário autenticado não encontrado no contexto da requisição."));
    }
}