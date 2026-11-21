package br.com.fatec.georural.service.usuario;

import br.com.fatec.georural.dto.client.keycloak.KeycloakAdminClient;
import br.com.fatec.georural.dto.request.usuario.EditarUsuarioRequest;
import br.com.fatec.georural.dto.response.usuario.UsuarioResponse;
import br.com.fatec.georural.entity.Perfil;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.PerfilRepository;
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

import static br.com.fatec.georural.service.auditoria.AuditoriaService.escapar;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioEdicaoService {

    private static final String FUNCAO_ADMIN = "ADMIN";

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditoriaService auditoriaService;
    private final UsuarioAutenticadoProvider usuarioAutenticadoProvider;
    private final TransactionTemplate transactionTemplate;

    public UsuarioResponse editar(Long usuarioId, EditarUsuarioRequest request) {
        if (request == null || (request.nome() == null && request.perfilId() == null)) {
            throw new RequisicaoInvalidaException("Informe ao menos 'nome' ou 'perfilId' para atualização.");
        }

        if (request.nome() != null && request.nome().trim().isEmpty()) {
            throw new RequisicaoInvalidaException("O nome do usuário não pode ser vazio.");
        }

        Perfil novoPerfil = null;
        if (request.perfilId() != null) {
            novoPerfil = perfilRepository.findById(request.perfilId())
                    .orElseThrow(() -> new RequisicaoInvalidaException(
                            "Perfil de acesso inválido ou inexistente: " + request.perfilId()
                    ));
        }

        final Perfil perfilDestino = novoPerfil;
        final String nomeDestino = request.nome() != null
                ? request.nome().trim().replaceAll("\\s+", " ")
                : null;

        AtomicBoolean roleTrocadaNoKeycloak = new AtomicBoolean(false);
        AtomicBoolean nomeAlteradoNoKeycloak = new AtomicBoolean(false);
        final String[] estadoAnteriorHolder = new String[3]; // [keycloakId, nomeAntigo, roleAntiga]
        final String[] estadoNovoHolder = new String[2];     // [nomeNovo, roleNova]

        try {
            return transactionTemplate.execute(status -> {
                Usuario usuario = usuarioRepository.findByIdComPerfil(usuarioId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + usuarioId));

                Perfil perfilAntigo = usuario.getPerfil();
                String nomeAntigo = usuario.getNome();
                String roleAntiga = perfilAntigo.getFuncao();

                boolean nomeAlterado = nomeDestino != null && !nomeDestino.equals(nomeAntigo);
                boolean perfilAlterado = perfilDestino != null && !perfilDestino.getId().equals(perfilAntigo.getId());

                if (!nomeAlterado && !perfilAlterado) {
                    return UsuarioResponse.de(usuario);
                }

                // Protecao contra auto-rebaixamento e rebaixamento do ultimo ADMIN ativo
                if (perfilAlterado && FUNCAO_ADMIN.equalsIgnoreCase(roleAntiga)
                        && !FUNCAO_ADMIN.equalsIgnoreCase(perfilDestino.getFuncao())) {

                    UsuarioAutenticado logado = usuarioAutenticadoProvider.obterOpcional().orElse(null);
                    if (logado != null && usuario.getId().equals(logado.usuId())) {
                        throw new ConflitoException("Um administrador não pode rebaixar o próprio perfil.");
                    }

                    if ("S".equalsIgnoreCase(usuario.getStatus())) {
                        List<Usuario> adminsAtivos = usuarioRepository.findAdminsAtivosComLock();
                        if (adminsAtivos.size() <= 1) {
                            throw new ConflitoException("Não é possível rebaixar o último administrador ativo do sistema.");
                        }
                    }
                }

                estadoAnteriorHolder[0] = usuario.getKeycloakId();
                estadoAnteriorHolder[1] = nomeAntigo;
                estadoAnteriorHolder[2] = roleAntiga;

                if (nomeAlterado) {
                    usuario.setNome(nomeDestino);
                }
                if (perfilAlterado) {
                    usuario.setPerfil(perfilDestino);
                }

                Usuario salvo = usuarioRepository.saveAndFlush(usuario);

                String roleNova = salvo.getPerfil().getFuncao();
                estadoNovoHolder[0] = salvo.getNome();
                estadoNovoHolder[1] = roleNova;

                String dadosAntigos = String.format(
                        "{\"usu_nome\":\"%s\",\"per_id\":%d,\"per_funcao\":\"%s\"}",
                        escapar(nomeAntigo),
                        perfilAntigo.getId(),
                        escapar(roleAntiga)
                );

                String dadosNovos = String.format(
                        "{\"usu_nome\":\"%s\",\"per_id\":%d,\"per_funcao\":\"%s\"}",
                        escapar(salvo.getNome()),
                        salvo.getPerfil().getId(),
                        escapar(roleNova)
                );

                auditoriaService.registrar("UPDATE", "USUARIO", salvo.getId(), dadosAntigos, dadosNovos);

                // Sincroniza Keycloak antes do commit do Oracle
                if (perfilAlterado) {
                    keycloakAdminClient.trocarRealmRole(usuario.getKeycloakId(), roleAntiga, roleNova);
                    roleTrocadaNoKeycloak.set(true);
                    keycloakAdminClient.revogarSessoes(usuario.getKeycloakId());
                }

                if (nomeAlterado) {
                    keycloakAdminClient.atualizarNome(usuario.getKeycloakId(), salvo.getNome());
                    nomeAlteradoNoKeycloak.set(true);
                }

                return UsuarioResponse.de(salvo);
            });
        } catch (RuntimeException ex) {
            // Se o Keycloak ja foi alterado mas o commit no Oracle falhou, reverte o Keycloak
            if (roleTrocadaNoKeycloak.get()) {
                reverterRoleKeycloakSilenciosamente(
                        estadoAnteriorHolder[0],
                        estadoNovoHolder[1],
                        estadoAnteriorHolder[2]
                );
            }
            if (nomeAlteradoNoKeycloak.get()) {
                reverterNomeKeycloakSilenciosamente(
                        estadoAnteriorHolder[0],
                        estadoAnteriorHolder[1]
                );
            }
            throw ex;
        }
    }

    private void reverterRoleKeycloakSilenciosamente(String keycloakId, String roleAplicada, String roleOriginal) {
        try {
            keycloakAdminClient.trocarRealmRole(keycloakId, roleAplicada, roleOriginal);
        } catch (Exception compEx) {
            log.error("Falha critica de compensacao: nao foi possivel reverter a role '{}' -> '{}' no Keycloak para sub={}.",
                    roleAplicada, roleOriginal, keycloakId, compEx);
        }
    }

    private void reverterNomeKeycloakSilenciosamente(String keycloakId, String nomeOriginal) {
        try {
            keycloakAdminClient.atualizarNome(keycloakId, nomeOriginal);
        } catch (Exception compEx) {
            log.error("Falha critica de compensacao: nao foi possivel reverter o nome no Keycloak para sub={}.",
                    keycloakId, compEx);
        }
    }
}