package br.com.fatec.georural.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.CallableStatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.sql.CallableStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidacaoUsuarioJwtFilterTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SegurancaExceptionHandlers exceptionHandlers;

    @Mock
    private FilterChain filterChain;

    private ValidacaoUsuarioJwtFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ValidacaoUsuarioJwtFilter(jdbcTemplate, exceptionHandlers);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComJwt(String sub, List<String> roles, Instant authTime) {
        var builder = Jwt.withTokenValue("token-teste")
                .header("alg", "RS256")
                .subject(sub)
                .claim("realm_access", Map.of("roles", roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (authTime != null) {
            builder.claim("auth_time", authTime.getEpochSecond());
        }
        Jwt jwt = builder.build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void stubPkgAuth(Long usuId, String status, String perFuncao) throws Exception {
        CallableStatement cs = mock(CallableStatement.class);
        if (usuId == null) {
            when(cs.getLong(2)).thenReturn(0L);
            when(cs.wasNull()).thenReturn(true);
        } else {
            when(cs.getLong(2)).thenReturn(usuId);
            when(cs.wasNull()).thenReturn(false);
            when(cs.getString(3)).thenReturn(status);
            when(cs.getString(4)).thenReturn(perFuncao);
        }

        when(jdbcTemplate.execute(
                eq("{call PKG_AUTH.consultar_por_sub(?, ?, ?, ?)}"),
                org.mockito.ArgumentMatchers.<CallableStatementCallback<UsuarioAutenticado>>any()))
                .thenAnswer(inv -> {
                    CallableStatementCallback<UsuarioAutenticado> cb = inv.getArgument(1);
                    return cb.doInCallableStatement(cs);
                });
    }

    @Test
    @DisplayName("Deve prosseguir, gravar UsuarioAutenticado na request e atualizar usu_ultimo_login apenas uma vez por auth_time")
    void deveValidarUsuarioEAtualizarUltimoLoginApenasUmaVez() throws Exception {
        Instant loginTime = Instant.parse("2026-11-18T15:00:00Z");
        autenticarComJwt("33333333-3333-3333-3333-333333333333", List.of("OPERADOR"), loginTime);
        stubPkgAuth(3L, "S", "OPERADOR");

        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/v1/catalogo");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilterInternal(req1, res1, filterChain);

        verify(filterChain, times(1)).doFilter(req1, res1);
        verify(jdbcTemplate, times(1)).update(contains("UPDATE USUARIO"), any(Timestamp.class), eq(3L), any(Timestamp.class));

        Object attr = req1.getAttribute(UsuarioAutenticadoProvider.REQUEST_ATTR);
        assertInstanceOf(UsuarioAutenticado.class, attr);
        assertEquals(3L, ((UsuarioAutenticado) attr).usuId());

        // Segunda requisicao com o mesmo auth_time nao dispara novo UPDATE
        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/catalogo");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilterInternal(req2, res2, filterChain);

        verify(jdbcTemplate, times(1)).update(contains("UPDATE USUARIO"), any(Timestamp.class), eq(3L), any(Timestamp.class));
    }

    @Test
    @DisplayName("Deve rejeitar com 401 quando usuario estiver desativado (usu_status = 'N')")
    void deveRejeitarUsuarioDesativado() throws Exception {
        autenticarComJwt("33333333-3333-3333-3333-333333333333", List.of("OPERADOR"), null);
        stubPkgAuth(3L, "N", "OPERADOR");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/catalogo");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, filterChain);

        verify(exceptionHandlers).commence(eq(req), eq(res), any());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Deve rejeitar com 401 quando papel do token divergir de per_funcao cadastrado no banco")
    void deveRejeitarPapelDivergente() throws Exception {
        autenticarComJwt("33333333-3333-3333-3333-333333333333", List.of("ADMIN"), null);
        stubPkgAuth(3L, "S", "OPERADOR");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/catalogo");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, filterChain);

        verify(exceptionHandlers).commence(eq(req), eq(res), any());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("Deve rejeitar com 401 quando token tiver mais de uma role do projeto")
    void deveRejeitarMultiplasRolesDoProjeto() throws Exception {
        autenticarComJwt("33333333-3333-3333-3333-333333333333", List.of("OPERADOR", "GESTOR"), null);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/catalogo");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, filterChain);

        verify(exceptionHandlers).commence(eq(req), eq(res), any());
        verify(filterChain, never()).doFilter(any(), any());
    }
}