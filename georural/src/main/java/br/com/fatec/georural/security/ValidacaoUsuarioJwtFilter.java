package br.com.fatec.georural.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValidacaoUsuarioJwtFilter extends OncePerRequestFilter {

    private static final Set<String> ROLES_PROJETO = Set.of(
            "ADMIN", "GESTOR", "OPERADOR", "ANALISTA", "AUDITOR"
    );

    private final JdbcTemplate jdbcTemplate;
    private final SegurancaExceptionHandlers segurancaExceptionHandlers;

    // Cache em memoria (usu_id -> ultimo auth_time processado) para evitar UPDATE a cada request
    private final ConcurrentHashMap<Long, Instant> ultimoAuthTimeProcessado = new ConcurrentHashMap<>();

    @Value("${georural.seguranca.liberar-api:false}")
    private boolean liberarApi;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (liberarApi) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            rejeitar401(request, response, "Token sem claim 'sub'.");
            return;
        }

        // 1. Extrai e valida as roles do projeto presentes no token
        Set<String> rolesProjetoNoToken = extrairRolesProjeto(jwt);
        if (rolesProjetoNoToken.isEmpty()) {
            rejeitar401(request, response, "Token não possui nenhuma das 5 roles do projeto.");
            return;
        }
        if (rolesProjetoNoToken.size() > 1) {
            rejeitar401(request, response, "Token possui mais de uma role do projeto: " + rolesProjetoNoToken);
            return;
        }
        String roleToken = rolesProjetoNoToken.iterator().next();

        // 2. Consulta o usuario no Oracle via PKG_AUTH.consultar_por_sub (US#13.03)
        UsuarioAutenticado usuario = consultarUsuarioPorSub(sub);
        if (usuario == null || usuario.usuId() == null) {
            rejeitar401(request, response, "Usuário não cadastrado na base para sub=" + sub);
            return;
        }

        // 3. Valida status ativo ('S')
        if (usuario.status() == null || !"S".equalsIgnoreCase(usuario.status().trim())) {
            rejeitar401(request, response, "Usuário desativado (usu_status=" + usuario.status() + ").");
            return;
        }

        // 4. Valida se o papel do token corresponde exatamente ao per_funcao cadastrado
        String perFuncaoBanco = usuario.perFuncao() != null ? usuario.perFuncao().trim().toUpperCase() : "";
        if (!roleToken.equals(perFuncaoBanco)) {
            rejeitar401(request, response,
                    "Papel do token (" + roleToken + ") diverge do perfil cadastrado (" + perFuncaoBanco + ").");
            return;
        }

        // 5. Atualiza usu_ultimo_login apenas no primeiro request apos o login (comparando auth_time)
        atualizarUltimoLoginSeNecessario(usuario.usuId(), jwt);

        // Disponibiliza o usuario resolvido para os controllers/services da requisicao (US#13.08)
        request.setAttribute(UsuarioAutenticadoProvider.REQUEST_ATTR, usuario);

        filterChain.doFilter(request, response);
    }

    private Set<String> extrairRolesProjeto(Jwt jwt) {
        Set<String> encontradas = new HashSet<>();
        Object realmAccessObj = jwt.getClaims().get("realm_access");
        if (realmAccessObj instanceof Map<?, ?> realmAccess) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof Collection<?> roles) {
                for (Object role : roles) {
                    if (role != null) {
                        String normalizada = role.toString().trim().toUpperCase();
                        if (normalizada.startsWith("ROLE_")) {
                            normalizada = normalizada.substring(5);
                        }
                        if (ROLES_PROJETO.contains(normalizada)) {
                            encontradas.add(normalizada);
                        }
                    }
                }
            }
        }
        return encontradas;
    }

    private UsuarioAutenticado consultarUsuarioPorSub(String sub) {
        return jdbcTemplate.execute(
                "{call PKG_AUTH.consultar_por_sub(?, ?, ?, ?)}",
                (CallableStatementCallback<UsuarioAutenticado>) cs -> {
                    cs.setString(1, sub);
                    cs.registerOutParameter(2, Types.NUMERIC);
                    cs.registerOutParameter(3, Types.VARCHAR);
                    cs.registerOutParameter(4, Types.VARCHAR);
                    cs.execute();

                    long id = cs.getLong(2);
                    if (cs.wasNull()) {
                        return null;
                    }
                    String status = cs.getString(3);
                    String perFuncao = cs.getString(4);
                    return new UsuarioAutenticado(id, sub, status, perFuncao);
                }
        );
    }

    private void atualizarUltimoLoginSeNecessario(Long usuId, Jwt jwt) {
        Instant authTime = extrairAuthTime(jwt);
        if (authTime == null) {
            return;
        }

        Instant anterior = ultimoAuthTimeProcessado.get(usuId);
        if (anterior != null && !authTime.isAfter(anterior)) {
            return;
        }

        LocalDateTime dataLogin = LocalDateTime.ofInstant(authTime, ZoneId.systemDefault());
        Timestamp tsLogin = Timestamp.valueOf(dataLogin);

        int atualizados = jdbcTemplate.update(
                """
                UPDATE USUARIO
                   SET usu_ultimo_login = ?
                 WHERE usu_id = ?
                   AND (usu_ultimo_login IS NULL OR usu_ultimo_login < ?)
                """,
                tsLogin, usuId, tsLogin
        );

        ultimoAuthTimeProcessado.compute(usuId, (id, atual) ->
                (atual == null || authTime.isAfter(atual)) ? authTime : atual
        );

        if (atualizados > 0) {
            log.debug("Atualizado usu_ultimo_login para usu_id={} com auth_time={}", usuId, dataLogin);
        }
    }

    private Instant extrairAuthTime(Jwt jwt) {
        Object claim = jwt.getClaim("auth_time");
        if (claim == null) {
            return null;
        }
        if (claim instanceof Instant instant) {
            return instant;
        }
        if (claim instanceof Number num) {
            return Instant.ofEpochSecond(num.longValue());
        }
        if (claim instanceof String str && !str.isBlank()) {
            try {
                return Instant.ofEpochSecond(Long.parseLong(str.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private void rejeitar401(
            HttpServletRequest request,
            HttpServletResponse response,
            String motivo) throws IOException {

        log.warn("Rejeitando requisicao 401 em {} {}: {}", request.getMethod(), request.getRequestURI(), motivo);
        SecurityContextHolder.clearContext();
        segurancaExceptionHandlers.commence(request, response, new BadCredentialsException(motivo));
    }
}