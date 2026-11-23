package br.com.fatec.georural.service.usuario;

import br.com.fatec.georural.dto.client.keycloak.KeycloakAdminClient;
import br.com.fatec.georural.dto.client.keycloak.KeycloakUserRepresentation;
import br.com.fatec.georural.dto.response.usuario.DivergenciaUsuarioResponse;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioDivergenciaService {

    public static final String TIPO_SEM_SUB = "USUARIO_SEM_SUB";
    public static final String TIPO_PERFIL_DIVERGENTE = "PERFIL_DIVERGENTE";
    public static final String TIPO_STATUS_DIVERGENTE = "STATUS_DIVERGENTE";

    private final UsuarioRepository usuarioRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    @Transactional(readOnly = true)
    public List<DivergenciaUsuarioResponse> listarDivergencias() {
        List<Usuario> usuariosOracle = usuarioRepository.findAllComPerfil();
        Map<String, KeycloakUserRepresentation> usuariosKeycloakPorId = keycloakAdminClient.listarUsuarios().stream()
                .filter(u -> u.id() != null && !u.id().isBlank())
                .collect(Collectors.toMap(KeycloakUserRepresentation::id, Function.identity(), (a, b) -> a));

        List<DivergenciaUsuarioResponse> divergencias = new ArrayList<>();

        for (Usuario u : usuariosOracle) {
            String sub = u.getKeycloakId();
            String perfilOracle = u.getPerfil() != null ? u.getPerfil().getFuncao().toUpperCase() : null;
            String statusOracle = u.getStatus() != null ? u.getStatus().toUpperCase() : null;
            boolean ativoOracle = "S".equals(statusOracle);

            // Caso 1: usuario sem sub ou sub inexistente no Keycloak
            if (sub == null || sub.isBlank() || !usuariosKeycloakPorId.containsKey(sub)) {
                divergencias.add(new DivergenciaUsuarioResponse(
                        TIPO_SEM_SUB,
                        u.getId(),
                        sub,
                        u.getEmail(),
                        u.getNome(),
                        perfilOracle,
                        Set.of(),
                        statusOracle,
                        null,
                        sub == null || sub.isBlank()
                                ? "Usuário cadastrado no Oracle sem usu_keycloak_id (sub)."
                                : "usu_keycloak_id registrado no Oracle não foi encontrado no realm do Keycloak."
                ));
                continue;
            }

            KeycloakUserRepresentation kcUser = usuariosKeycloakPorId.get(sub);
            Set<String> rolesKc = keycloakAdminClient.listarNomesRealmRolesDoUsuario(sub);

            // Caso 2: per_id / per_funcao diferente da role no Keycloak
            boolean perfilAlinhado = perfilOracle != null
                    && rolesKc.size() == 1
                    && rolesKc.contains(perfilOracle);

            if (!perfilAlinhado) {
                divergencias.add(new DivergenciaUsuarioResponse(
                        TIPO_PERFIL_DIVERGENTE,
                        u.getId(),
                        sub,
                        u.getEmail(),
                        u.getNome(),
                        perfilOracle,
                        rolesKc,
                        statusOracle,
                        kcUser.enabled(),
                        "Perfil no Oracle ('" + perfilOracle + "') diverge das realm roles no Keycloak (" + rolesKc + ")."
                ));
            }

            // Caso 3: enabled no Keycloak diferente de usu_status no Oracle
            boolean enabledKc = Boolean.TRUE.equals(kcUser.enabled());
            if (ativoOracle != enabledKc) {
                divergencias.add(new DivergenciaUsuarioResponse(
                        TIPO_STATUS_DIVERGENTE,
                        u.getId(),
                        sub,
                        u.getEmail(),
                        u.getNome(),
                        perfilOracle,
                        rolesKc,
                        statusOracle,
                        kcUser.enabled(),
                        "Status no Oracle ('" + statusOracle + "') diverge de enabled no Keycloak (" + kcUser.enabled() + ")."
                ));
            }
        }

        return divergencias;
    }
}