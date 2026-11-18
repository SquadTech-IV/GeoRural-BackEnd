package br.com.fatec.georural.security;

import br.com.fatec.georural.entity.Auditoria;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.repository.AuditoriaRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaAcessoNegadoService {

    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAcessoNegado(HttpServletRequest request, Authentication authentication, String motivo) {
        try {
            Auditoria aud = new Auditoria();
            aud.setOperacao("ACESSO_NEGADO");
            aud.setEntidadeAfetada("AUTORIZACAO");
            aud.setIpOrigem(extrairIp(request));
            aud.setDataHora(LocalDateTime.now());

            String sub = null;
            String roles = "";

            if (authentication != null) {
                roles = authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(","));

                if (authentication.getPrincipal() instanceof Jwt jwt) {
                    sub = jwt.getSubject();
                } else {
                    sub = authentication.getName();
                }
            }

            if (sub != null && !sub.isBlank()) {
                Usuario usuario = usuarioRepository.findByKeycloakIdComPerfil(sub).orElse(null);
                if (usuario != null) {
                    aud.setUsuario(usuario);
                    aud.setUsuarioNome(usuario.getNome());
                } else {
                    aud.setUsuarioNome(sub.length() > 120 ? sub.substring(0, 120) : sub);
                }
            } else {
                aud.setUsuarioNome("anonimo");
            }

            String dados = String.format(
                    "{\"metodo\":\"%s\",\"caminho\":\"%s\",\"sub\":\"%s\",\"roles\":\"%s\",\"motivo\":\"%s\"}",
                    escapar(request.getMethod()),
                    escapar(request.getRequestURI()),
                    escapar(sub),
                    escapar(roles),
                    escapar(motivo)
            );
            aud.setDadosNovos(dados);

            auditoriaRepository.save(aud);
        } catch (Exception e) {
            log.warn("Nao foi possivel gravar auditoria de acesso negado para {}: {}",
                    request.getRequestURI(), e.getMessage());
        }
    }

    private String extrairIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}