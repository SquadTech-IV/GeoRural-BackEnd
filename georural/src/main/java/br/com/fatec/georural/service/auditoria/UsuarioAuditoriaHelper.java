package br.com.fatec.georural.service.auditoria;

import br.com.fatec.georural.entity.Auditoria;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.repository.AuditoriaRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.security.UsuarioAutenticado;
import br.com.fatec.georural.security.UsuarioAutenticadoProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class UsuarioAuditoriaHelper {

    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    public void registrar(
            String operacao,
            String entidadeAfetada,
            Long registroId,
            String dadosAntigos,
            String dadosNovos
    ) {
        Auditoria aud = new Auditoria();
        aud.setOperacao(operacao);
        aud.setEntidadeAfetada(entidadeAfetada);
        aud.setRegistroId(registroId);
        aud.setDadosAntigos(dadosAntigos);
        aud.setDadosNovos(dadosNovos);
        aud.setDataHora(LocalDateTime.now());
        aud.setIpOrigem(extrairIpAtual());

        UsuarioAutenticado auth = usuarioAutenticadoProvider.obterOpcional().orElse(null);
        if (auth != null && auth.usuId() != null) {
            Usuario ator = usuarioRepository.findById(auth.usuId()).orElse(null);
            if (ator != null) {
                aud.setUsuario(ator);
                aud.setUsuarioNome(ator.getNome());
            } else {
                aud.setUsuarioNome(auth.keycloakId());
            }
        } else {
            aud.setUsuarioNome("sistema");
        }

        auditoriaRepository.save(aud);
    }

    public static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extrairIpAtual() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            HttpServletRequest request = servletAttrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }
}