package br.com.fatec.georural.service.usuario;

import br.com.fatec.georural.dto.client.keycloak.KeycloakAdminClient;
import br.com.fatec.georural.dto.response.usuario.UsuarioResponse;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.security.UsuarioAutenticado;
import br.com.fatec.georural.security.UsuarioAutenticadoProvider;
import br.com.fatec.georural.service.auditoria.AuditoriaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioStatusService {

    private static final String STATUS_ATIVO = "S";
    private static final String STATUS_INATIVO = "N";
    private static final String FUNCAO_ADMIN = "ADMIN";

    private final UsuarioRepository usuarioRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final TransactionTemplate transactionTemplate;

    public UsuarioResponse desativar(Long usuarioId) {
        AtomicBoolean statusAlteradoNoKeycloak = new AtomicBoolean(false);
        final String[] keycloakIdHolder = new String[1];

        try {
            return transactionTemplate.execute(txStatus -> {
                Usuario usuario = usuarioRepository.findByIdComPerfil(usuarioId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + usuarioId));

                if (STATUS_INATIVO.equalsIgnoreCase(usuario.getStatus())) {
                    throw new ConflitoException("O usuário já se encontra desativado.");
                }

                UsuarioAutenticado logado = usuarioAutenticadoProvider.obterOpcional().orElse(null);
                if (logado != null && usuario.getId().equals(logado.usuId())) {
                    throw new ConflitoException("Um administrador não pode desativar a própria conta.");
                }

                if (usuario.getPerfil() != null && FUNCAO_ADMIN.equalsIgnoreCase(usuario.getPerfil().getFuncao())) {
                    List<Usuario> adminsAtivos = usuarioRepository.findAdminsAtivosComLock();
                    if (adminsAtivos.size() <= 1) {
                        throw new ConflitoException("Não é possível desativar o último administrador ativo do sistema.");
                    }
                }

                keycloakIdHolder[0] = usuario.getKeycloakId();
                usuario.setStatus(STATUS_INATIVO);
                Usuario salvo = usuarioRepository.saveAndFlush(usuario);

                auditoriaService.registrar(
                        "DESATIVACAO",
                        "USUARIO",
                        salvo.getId(),
                        "{\"usu_status\":\"S\"}",
                        "{\"usu_status\":\"N\"}"
                );

                keycloakAdminClient.alterarStatus(usuario.getKeycloakId(), false);
                statusAlteradoNoKeycloak.set(true);
                keycloakAdminClient.revogarSessoes(usuario.getKeycloakId());

                return UsuarioResponse.de(salvo);
            });
        } catch (RuntimeException ex) {
            if (statusAlteradoNoKeycloak.get() && keycloakIdHolder[0] != null) {
                reverterStatusKeycloakSilenciosamente(keycloakIdHolder[0], true);
            }
            throw ex;
        }
    }

    public UsuarioResponse reativar(Long usuarioId) {
        AtomicBoolean statusAlteradoNoKeycloak = new AtomicBoolean(false);
        final String[] keycloakIdHolder = new String[1];

        try {
            return transactionTemplate.execute(txStatus -> {
                Usuario usuario = usuarioRepository.findByIdComPerfil(usuarioId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + usuarioId));

                if (STATUS_ATIVO.equalsIgnoreCase(usuario.getStatus())) {
                    throw new ConflitoException("O usuário já se encontra ativo.");
                }

                keycloakIdHolder[0] = usuario.getKeycloakId();
                usuario.setStatus(STATUS_ATIVO);
                Usuario salvo = usuarioRepository.saveAndFlush(usuario);

                auditoriaService.registrar(
                        "REATIVACAO",
                        "USUARIO",
                        salvo.getId(),
                        "{\"usu_status\":\"N\"}",
                        "{\"usu_status\":\"S\"}"
                );

                keycloakAdminClient.alterarStatus(usuario.getKeycloakId(), true);
                statusAlteradoNoKeycloak.set(true);

                return UsuarioResponse.de(salvo);
            });
        } catch (RuntimeException ex) {
            if (statusAlteradoNoKeycloak.get() && keycloakIdHolder[0] != null) {
                reverterStatusKeycloakSilenciosamente(keycloakIdHolder[0], false);
            }
            throw ex;
        }
    }

    private void reverterStatusKeycloakSilenciosamente(String keycloakId, boolean statusOriginal) {
        try {
            keycloakAdminClient.alterarStatus(keycloakId, statusOriginal);
        } catch (Exception compEx) {
            log.error("Falha critica de compensacao: nao foi possivel reverter enabled={} no Keycloak para sub={}.",
                    statusOriginal, keycloakId, compEx);
        }
    }
}