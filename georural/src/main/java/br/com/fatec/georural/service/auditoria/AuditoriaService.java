package br.com.fatec.georural.service.auditoria;

import br.com.fatec.georural.entity.Auditoria;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.repository.AuditoriaRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.security.UsuarioAutenticado;
import br.com.fatec.georural.security.UsuarioAutenticadoProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    /**
     * Grava um evento na trilha de auditoria obrigatoriamente dentro da transacao ativa.
     * Se a gravacao falhar, a excecao propaga e desfaz a operacao inteira.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(
            String operacao,
            String entidadeAfetada,
            Long registroId,
            String dadosAntigosJson,
            String dadosNovosJson
    ) {
        registrar(operacao, entidadeAfetada, registroId, dadosAntigosJson, dadosNovosJson, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(
            String operacao,
            String entidadeAfetada,
            Long registroId,
            String dadosAntigosJson,
            String dadosNovosJson,
            LocalDateTime dataHora
    ) {
        Auditoria aud = new Auditoria();
        aud.setOperacao(operacao);
        aud.setEntidadeAfetada(entidadeAfetada);
        aud.setRegistroId(registroId);
        aud.setDadosAntigos(dadosAntigosJson);
        aud.setDadosNovos(dadosNovosJson);
        aud.setDataHora(dataHora != null ? dataHora : LocalDateTime.now());
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

        auditoriaRepository.saveAndFlush(aud);
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