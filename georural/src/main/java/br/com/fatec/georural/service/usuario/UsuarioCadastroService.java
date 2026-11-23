package br.com.fatec.georural.service.usuario;

import br.com.fatec.georural.dto.client.keycloak.KeycloakAdminClient;
import br.com.fatec.georural.dto.client.keycloak.KeycloakUserRepresentation;
import br.com.fatec.georural.dto.request.usuario.CriarUsuarioRequest;
import br.com.fatec.georural.dto.response.usuario.UsuarioResponse;
import br.com.fatec.georural.entity.Perfil;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.PerfilRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.service.auditoria.AuditoriaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static br.com.fatec.georural.service.auditoria.AuditoriaService.escapar;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioCadastroService {

    private static final String REQUIRED_ACTION_UPDATE_PASSWORD = "UPDATE_PASSWORD";

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AuditoriaService auditoriaService;
    private final TransactionTemplate transactionTemplate;

    public UsuarioResponse cadastrar(CriarUsuarioRequest request) {
        String nomeNormalizado = normalizarNome(request.nome());
        String emailNormalizado = normalizarEmail(request.email());

        Perfil perfil = perfilRepository.findById(request.perfilId())
                .orElseThrow(() -> new RequisicaoInvalidaException(
                        "Perfil de acesso inválido ou inexistente: " + request.perfilId()
                ));

        if (usuarioRepository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new ConflitoException("Já existe um usuário cadastrado com o e-mail '" + emailNormalizado + "'.");
        }

        // 1. Cria no Keycloak (com requiredAction UPDATE_PASSWORD e realm role)
        String sub = keycloakAdminClient.criarUsuario(nomeNormalizado, emailNormalizado, perfil.getFuncao());

        // 2. Persiste no Oracle + AUDITORIA na mesma transacao; compensa no Keycloak se falhar
        Usuario salvo;
        try {
            salvo = transactionTemplate.execute(status -> {
                Usuario novo = new Usuario();
                novo.setPerfil(perfil);
                novo.setKeycloakId(sub);
                novo.setNome(nomeNormalizado);
                novo.setEmail(emailNormalizado);
                novo.setStatus("S");
                novo.setDataCadastro(LocalDateTime.now());

                Usuario persistido = usuarioRepository.save(novo);

                String dadosNovos = String.format(
                        "{\"usu_id\":%d,\"usu_nome\":\"%s\",\"usu_email\":\"%s\",\"per_id\":%d,\"per_funcao\":\"%s\",\"usu_status\":\"S\"}",
                        persistido.getId(),
                        escapar(persistido.getNome()),
                        escapar(persistido.getEmail()),
                        perfil.getId(),
                        escapar(perfil.getFuncao())
                );

                auditoriaService.registrar("INSERT", "USUARIO", persistido.getId(), null, dadosNovos);
                return persistido;
            });
        } catch (RuntimeException ex) {
            log.error("Falha ao gravar usuario '{}' no Oracle; executando compensacao no Keycloak (sub={}).",
                    emailNormalizado, sub, ex);
            compensarExclusaoKeycloak(sub);
            throw ex;
        }

        // 3. Dispara e-mail de definicao de senha (nao desfaz cadastro se o SMTP falhar)
        String avisoEmail = null;
        try {
            keycloakAdminClient.enviarEmailAcao(sub, List.of(REQUIRED_ACTION_UPDATE_PASSWORD));
        } catch (RuntimeException ex) {
            log.warn("Usuário {} criado (sub={}), mas houve falha ao enviar e-mail de convite: {}",
                    salvo.getId(), sub, ex.getMessage());
            avisoEmail = "Usuário criado com sucesso, mas não foi possível enviar o e-mail de definição de senha agora. Utilize o reenvio de convite.";
        }

        return UsuarioResponse.de(salvo, true, avisoEmail);
    }

    public UsuarioResponse reenviarConvite(Long usuarioId) {
        Usuario usuario = usuarioRepository.findByIdComPerfil(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado: " + usuarioId));

        if (!"S".equalsIgnoreCase(usuario.getStatus())) {
            throw new RequisicaoInvalidaException("Não é possível reenviar convite para um usuário desativado.");
        }

        KeycloakUserRepresentation kcUser = keycloakAdminClient.buscarUsuarioPorId(usuario.getKeycloakId());
        boolean pendenteDefinicaoSenha = kcUser.requiredActions() != null
                && kcUser.requiredActions().contains(REQUIRED_ACTION_UPDATE_PASSWORD);

        if (!pendenteDefinicaoSenha) {
            throw new RequisicaoInvalidaException("Este usuário já definiu sua senha inicial e não possui convite pendente.");
        }

        keycloakAdminClient.enviarEmailAcao(usuario.getKeycloakId(), List.of(REQUIRED_ACTION_UPDATE_PASSWORD));

        transactionTemplate.executeWithoutResult(status -> {
            String dadosNovos = String.format(
                    "{\"acao\":\"REENVIO_CONVITE\",\"usu_id\":%d,\"usu_email\":\"%s\"}",
                    usuario.getId(),
                    escapar(usuario.getEmail())
            );
            auditoriaService.registrar("REENVIO_CONVITE", "USUARIO", usuario.getId(), null, dadosNovos);
        });

        return UsuarioResponse.de(usuario, true, null);
    }

    private void compensarExclusaoKeycloak(String sub) {
        try {
            keycloakAdminClient.excluirUsuario(sub);
        } catch (Exception compEx) {
            log.error("Falha critica na compensacao: usuario sub={} permaneceu orfao no Keycloak.", sub, compEx);
        }
    }

    private String normalizarNome(String nome) {
        if (nome == null || nome.trim().isEmpty()) {
            throw new RequisicaoInvalidaException("O nome do usuário é obrigatório.");
        }
        return nome.trim().replaceAll("\\s+", " ");
    }

    private String normalizarEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new RequisicaoInvalidaException("O e-mail do usuário é obrigatório.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}