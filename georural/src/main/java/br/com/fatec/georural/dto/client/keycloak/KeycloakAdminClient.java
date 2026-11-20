package br.com.fatec.georural.dto.client.keycloak;

import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.KeycloakIndisponivelException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KeycloakAdminClient {

    private static final Set<String> ROLES_SISTEMA = Set.of(
            "ADMIN", "GESTOR", "OPERADOR", "ANALISTA", "AUDITOR"
    );

    private final RestClient restClient;
    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakAdminClient(
            @Value("${georural.keycloak.admin.server-url:http://localhost:8180}") String serverUrl,
            @Value("${georural.keycloak.admin.realm:georural}") String realm,
            @Value("${georural.keycloak.admin.client-id:georural-admin-sa}") String clientId,
            @Value("${georural.keycloak.admin.client-secret:}") String clientSecret,
            @Value("${georural.keycloak.admin.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${georural.keycloak.admin.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(this.serverUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Cria o usuario no Keycloak com a required action UPDATE_PASSWORD e atribui a realm role.
     * Retorna o sub (UUID) gerado pelo Keycloak.
     */
    public String criarUsuario(String nome, String emailNormalizado, String perfilFuncao) {
        NomeSplit nomeSplit = separarNome(nome);
        KeycloakUserRepresentation payload = new KeycloakUserRepresentation(
                null,
                emailNormalizado,
                emailNormalizado,
                nomeSplit.firstName(),
                nomeSplit.lastName(),
                true,
                false,
                List.of("UPDATE_PASSWORD")
        );

        String sub;
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            sub = extrairIdDoLocation(response.getHeaders().getLocation());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ConflitoException("Já existe um usuário cadastrado com este e-mail no provedor de identidade.");
            }
            throw traduzirErroCliente(e, "criar usuário no Keycloak");
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak ao criar usuário.", e);
        }

        try {
            atribuirRealmRole(sub, perfilFuncao);
            return sub;
        } catch (RuntimeException e) {
            log.warn("Falha ao atribuir role '{}' ao usuario {} recem-criado; executando compensacao.", perfilFuncao, sub);
            excluirUsuarioSilenciosamente(sub);
            throw e;
        }
    }

    /**
     * Atualiza firstName e lastName do usuario no Keycloak preservando os demais campos.
     */
    public void atualizarNome(String keycloakId, String novoNome) {
        KeycloakUserRepresentation atual = buscarUsuarioPorId(keycloakId);
        NomeSplit nomeSplit = separarNome(novoNome);

        KeycloakUserRepresentation atualizado = new KeycloakUserRepresentation(
                atual.id(),
                atual.username(),
                atual.email(),
                nomeSplit.firstName(),
                nomeSplit.lastName(),
                atual.enabled(),
                atual.emailVerified(),
                atual.requiredActions()
        );

        atualizarUsuario(keycloakId, atualizado, "atualizar nome no Keycloak");
    }

    /**
     * Habilita ou desabilita o usuario no Keycloak preservando os demais campos.
     */
    public void alterarStatus(String keycloakId, boolean ativo) {
        KeycloakUserRepresentation atual = buscarUsuarioPorId(keycloakId);

        KeycloakUserRepresentation atualizado = new KeycloakUserRepresentation(
                atual.id(),
                atual.username(),
                atual.email(),
                atual.firstName(),
                atual.lastName(),
                ativo,
                atual.emailVerified(),
                atual.requiredActions()
        );

        atualizarUsuario(keycloakId, atualizado, ativo ? "reativar usuário no Keycloak" : "desativar usuário no Keycloak");
    }

    /**
     * Substitui a realm role do usuario no Keycloak (remove roles de sistema anteriores e adiciona a nova).
     */
    public void trocarRealmRole(String keycloakId, String roleAntiga, String roleNova) {
        List<KeycloakRoleRepresentation> rolesAtuais = listarRealmRolesDoUsuario(keycloakId);
        List<KeycloakRoleRepresentation> paraRemover = rolesAtuais.stream()
                .filter(r -> r.name() != null && (r.name().equalsIgnoreCase(roleAntiga) || ROLES_SISTEMA.contains(r.name().toUpperCase())))
                .toList();

        if (!paraRemover.isEmpty()) {
            try {
                restClient.method(HttpMethod.DELETE)
                        .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakId)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(paraRemover)
                        .retrieve()
                        .toBodilessEntity();
            } catch (HttpClientErrorException e) {
                throw traduzirErroCliente(e, "remover role anterior no Keycloak");
            } catch (RestClientException e) {
                throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak ao remover role anterior.", e);
            }
        }

        atribuirRealmRole(keycloakId, roleNova);
    }

    /**
     * Encerra todas as sessoes ativas do usuario no Keycloak.
     */
    public void revogarSessoes(String keycloakId) {
        try {
            restClient.post()
                    .uri("/admin/realms/{realm}/users/{id}/logout", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw traduzirErroCliente(e, "revogar sessões no Keycloak");
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak ao revogar sessões.", e);
        }
    }

    /**
     * Dispara e-mail de acao requerida (ex: UPDATE_PASSWORD).
     */
    public void enviarEmailAcao(String keycloakId, List<String> actions) {
        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{id}/execute-actions-email", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(actions)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw traduzirErroCliente(e, "enviar e-mail de definição de senha no Keycloak");
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak ao enviar e-mail de ação.", e);
        }
    }

    /**
     * Exclui o usuario no Keycloak. Utilizado exclusivamente para compensacao quando a gravacao local falha.
     */
    public void excluirUsuario(String keycloakId) {
        try {
            restClient.delete()
                    .uri("/admin/realms/{realm}/users/{id}", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // Ja nao existe no Keycloak; compensacao concluida.
        } catch (HttpClientErrorException e) {
            throw traduzirErroCliente(e, "excluir usuário no Keycloak durante compensação");
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak durante compensação.", e);
        }
    }

    public KeycloakUserRepresentation buscarUsuarioPorId(String keycloakId) {
        try {
            KeycloakUserRepresentation user = restClient.get()
                    .uri("/admin/realms/{realm}/users/{id}", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .body(KeycloakUserRepresentation.class);
            if (user == null) {
                throw new RecursoNaoEncontradoException("Usuário não encontrado no Keycloak.");
            }
            return user;
        } catch (HttpClientErrorException e) {
            throw traduzirErroCliente(e, "consultar usuário no Keycloak");
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak ao consultar usuário.", e);
        }
    }

    public List<KeycloakUserRepresentation> listarUsuarios() {
        try {
            List<KeycloakUserRepresentation> users = restClient.get()
                    .uri("/admin/realms/{realm}/users?max=1000", realm)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return users != null ? users : List.of();
        } catch (HttpClientErrorException e) {
            throw traduzirErroCliente(e, "listar usuários no Keycloak");
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak ao listar usuários.", e);
        }
    }

    public Set<String> listarNomesRealmRolesDoUsuario(String keycloakId) {
        return listarRealmRolesDoUsuario(keycloakId).stream()
                .map(KeycloakRoleRepresentation::name)
                .filter(name -> name != null && ROLES_SISTEMA.contains(name.toUpperCase()))
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private void atribuirRealmRole(String keycloakId, String roleName) {
        KeycloakRoleRepresentation role = buscarRealmRole(roleName);
        try {
            restClient.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw traduzirErroCliente(e, "atribuir role no Keycloak");
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak ao atribuir role.", e);
        }
    }

    private KeycloakRoleRepresentation buscarRealmRole(String roleName) {
        try {
            KeycloakRoleRepresentation role = restClient.get()
                    .uri("/admin/realms/{realm}/roles/{roleName}", realm, roleName.toUpperCase())
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .body(KeycloakRoleRepresentation.class);
            if (role == null) {
                throw new RecursoNaoEncontradoException("Perfil '" + roleName + "' não encontrado no Keycloak.");
            }
            return role;
        } catch (HttpClientErrorException e) {
            throw traduzirErroCliente(e, "consultar role '" + roleName + "' no Keycloak");
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak ao consultar role.", e);
        }
    }

    private List<KeycloakRoleRepresentation> listarRealmRolesDoUsuario(String keycloakId) {
        try {
            List<KeycloakRoleRepresentation> roles = restClient.get()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return roles != null ? roles : List.of();
        } catch (HttpClientErrorException e) {
            throw traduzirErroCliente(e, "listar roles do usuário no Keycloak");
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak ao listar roles do usuário.", e);
        }
    }

    private void atualizarUsuario(String keycloakId, KeycloakUserRepresentation payload, String contexto) {
        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{id}", realm, keycloakId)
                    .header(HttpHeaders.AUTHORIZATION, bearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw traduzirErroCliente(e, contexto);
        } catch (RestClientException e) {
            throw new KeycloakIndisponivelException("Falha de comunicação com o Keycloak ao " + contexto + ".", e);
        }
    }

    private void excluirUsuarioSilenciosamente(String sub) {
        try {
            excluirUsuario(sub);
        } catch (Exception ex) {
            log.error("Falha critica de compensacao: nao foi possivel excluir o usuario {} no Keycloak.", sub, ex);
        }
    }

    private String bearerToken() {
        return "Bearer " + obterAccessTokenServiceAccount();
    }

    private String obterAccessTokenServiceAccount() {
        Instant agora = Instant.now();
        if (cachedAccessToken != null && agora.isBefore(tokenExpiresAt)) {
            return cachedAccessToken;
        }

        synchronized (this) {
            agora = Instant.now();
            if (cachedAccessToken != null && agora.isBefore(tokenExpiresAt)) {
                return cachedAccessToken;
            }

            if (clientSecret == null || clientSecret.isBlank()) {
                throw new KeycloakIndisponivelException(
                        "Credencial da service account do Keycloak (KC_ADMIN_CLIENT_SECRET) não configurada."
                );
            }

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);

            try {
                TokenResponse tokenResp = restClient.post()
                        .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(TokenResponse.class);

                if (tokenResp == null || tokenResp.accessToken() == null || tokenResp.accessToken().isBlank()) {
                    throw new KeycloakIndisponivelException("Keycloak retornou token vazio para a service account.");
                }

                long validadeSegundos = tokenResp.expiresIn() != null ? tokenResp.expiresIn() : 60L;
                this.cachedAccessToken = tokenResp.accessToken();
                this.tokenExpiresAt = Instant.now().plusSeconds(Math.max(10L, validadeSegundos - 15L));
                return this.cachedAccessToken;
            } catch (ResourceAccessException e) {
                throw new KeycloakIndisponivelException("Timeout ou falha de rede ao autenticar service account no Keycloak.", e);
            } catch (RestClientException e) {
                throw new KeycloakIndisponivelException("Falha ao autenticar service account no Keycloak.", e);
            }
        }
    }

    private RuntimeException traduzirErroCliente(HttpClientErrorException e, String contexto) {
        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new RecursoNaoEncontradoException("Usuário ou recurso não encontrado no Keycloak ao " + contexto + ".");
        }
        if (e.getStatusCode() == HttpStatus.CONFLICT) {
            return new ConflitoException("Conflito de dados no Keycloak ao " + contexto + ".");
        }
        return new KeycloakIndisponivelException(
                "Erro HTTP " + e.getStatusCode().value() + " ao " + contexto + ".", e
        );
    }

    private static String extrairIdDoLocation(URI location) {
        if (location == null || location.getPath() == null) {
            throw new KeycloakIndisponivelException("Keycloak não retornou o header Location com o ID do usuário criado.");
        }
        String path = location.getPath();
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx == path.length() - 1) {
            throw new KeycloakIndisponivelException("Header Location inválido retornado pelo Keycloak: " + path);
        }
        return path.substring(idx + 1);
    }

    private static NomeSplit separarNome(String nomeCompleto) {
        String limpo = nomeCompleto == null ? "" : nomeCompleto.trim().replaceAll("\\s+", " ");
        int espaco = limpo.indexOf(' ');
        if (espaco < 0) {
            return new NomeSplit(limpo, "");
        }
        return new NomeSplit(limpo.substring(0, espaco), limpo.substring(espaco + 1));
    }

    private record NomeSplit(String firstName, String lastName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {
    }
}