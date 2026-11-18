package br.com.fatec.georural.security;

import br.com.fatec.georural.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = SecurityMatrizPermissoesTest.TestConfig.class)
@TestPropertySource(properties = "georural.seguranca.liberar-api=false")
class SecurityMatrizPermissoesTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private AuditoriaAcessoNegadoService auditoriaAcessoNegadoService;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(jwtDecoder, auditoriaAcessoNegadoService);
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    private String tokenComRole(String role) {
        String tokenStr = "token-" + role;
        Jwt jwt = Jwt.withTokenValue(tokenStr)
                .header("alg", "RS256")
                .subject("sub-" + role)
                .claim("realm_access", Map.of("roles", List.of(role)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(jwtDecoder.decode(tokenStr)).thenReturn(jwt);
        return "Bearer " + tokenStr;
    }

    @Test
    @DisplayName("Rotas abertas (/v3/api-docs e /actuator/health) respondem 200 sem token")
    void rotasAbertas_devemResponder200SemToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rota protegida sem token deve retornar 401 com payload ErroResponse")
    void rotaProtegida_semToken_deveRetornar401() throws Exception {
        mockMvc.perform(get("/api/v1/catalogo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.caminho").value("/api/v1/catalogo"));
    }

    @Test
    @DisplayName("/api/teste/** permite ADMIN (200) e bloqueia GESTOR/OPERADOR/ANALISTA/AUDITOR (403 + auditoria)")
    void rotasTeste_apenasAdmin() throws Exception {
        mockMvc.perform(get("/api/teste/checklist")
                        .header("Authorization", tokenComRole("ADMIN")))
                .andExpect(status().isOk());

        for (String role : List.of("GESTOR", "OPERADOR", "ANALISTA", "AUDITOR")) {
            mockMvc.perform(get("/api/teste/checklist")
                            .header("Authorization", tokenComRole(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        verify(auditoriaAcessoNegadoService, times(4)).registrarAcessoNegado(any(), any(), any());
    }

    @Test
    @DisplayName("Publicacao de versao permite ADMIN e GESTOR (200) e bloqueia OPERADOR, ANALISTA e AUDITOR (403)")
    void publicarVersao_apenasAdminEGestor() throws Exception {
        for (String permitido : List.of("ADMIN", "GESTOR")) {
            mockMvc.perform(post("/api/versoes/10/publicar")
                            .header("Authorization", tokenComRole(permitido)))
                    .andExpect(status().isOk());
        }

        for (String negado : List.of("OPERADOR", "ANALISTA", "AUDITOR")) {
            mockMvc.perform(post("/api/versoes/10/publicar")
                            .header("Authorization", tokenComRole(negado)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Upload e alteracao de quarentena permitem ADMIN, GESTOR e OPERADOR (200) e bloqueiam ANALISTA e AUDITOR (403)")
    void operacaoEQuarentena_permiteOperadorGestorAdmin() throws Exception {
        for (String permitido : List.of("ADMIN", "GESTOR", "OPERADOR")) {
            mockMvc.perform(post("/api/arquivos/1/processar")
                            .header("Authorization", tokenComRole(permitido)))
                    .andExpect(status().isOk());

            mockMvc.perform(patch("/api/v1/qualidade/quarentena/5/situacao")
                            .header("Authorization", tokenComRole(permitido))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"qua_situacao\":\"CORRIGIDO\"}"))
                    .andExpect(status().isOk());
        }

        for (String negado : List.of("ANALISTA", "AUDITOR")) {
            mockMvc.perform(post("/api/arquivos/1/processar")
                            .header("Authorization", tokenComRole(negado)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(patch("/api/v1/qualidade/quarentena/5/situacao")
                            .header("Authorization", tokenComRole(negado))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"qua_situacao\":\"CORRIGIDO\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Linhagem e Qualidade permitem AUDITOR (200) e bloqueiam ANALISTA (403)")
    void linhagemEQualidade_bloqueiaAnalistaEPermiteAuditor() throws Exception {
        mockMvc.perform(get("/api/v1/linhagem/resultados")
                        .header("Authorization", tokenComRole("AUDITOR")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/qualidade/quarentena")
                        .header("Authorization", tokenComRole("AUDITOR")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/linhagem/resultados")
                        .header("Authorization", tokenComRole("ANALISTA")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/qualidade/quarentena")
                        .header("Authorization", tokenComRole("ANALISTA")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Catalogo e Download permitem todos os 5 perfis autenticados (200)")
    void catalogoEDownload_permiteTodosOsPerfis() throws Exception {
        for (String role : List.of("ADMIN", "GESTOR", "OPERADOR", "ANALISTA", "AUDITOR")) {
            String auth = tokenComRole(role);
            mockMvc.perform(get("/api/v1/catalogo").header("Authorization", auth))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/arquivos/1/download").header("Authorization", auth))
                    .andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("Rota nao mapeada na matriz deve ser negada por padrao (denyAll -> 403)")
    void rotaNaoMapeada_deveRetornar403() throws Exception {
        mockMvc.perform(get("/api/rota-inexistente-na-matriz")
                        .header("Authorization", tokenComRole("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, KeycloakJwtAuthenticationConverter.class, SegurancaExceptionHandlers.class})
    static class TestConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        AuditoriaAcessoNegadoService auditoriaAcessoNegadoService() {
            return mock(AuditoriaAcessoNegadoService.class);
        }

        @Bean
        ValidacaoUsuarioJwtFilter validacaoUsuarioJwtFilter() {
            return new ValidacaoUsuarioJwtFilter(mock(JdbcTemplate.class), mock(SegurancaExceptionHandlers.class)) {
                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws jakarta.servlet.ServletException, java.io.IOException {
                    chain.doFilter(req, res);
                }
            };
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        DummyEndpointsController dummyEndpointsController() {
            return new DummyEndpointsController();
        }
    }

    @RestController
    static class DummyEndpointsController {
        @GetMapping("/v3/api-docs")
        String docs() { return "ok"; }

        @GetMapping("/actuator/health")
        String health() { return "UP"; }

        @GetMapping("/api/teste/checklist")
        String teste() { return "ok"; }

        @PostMapping("/api/versoes/{id}/publicar")
        String publicar(@PathVariable Long id) { return "ok"; }

        @PostMapping("/api/arquivos/{id}/processar")
        String processar(@PathVariable Long id) { return "ok"; }

        @PatchMapping("/api/v1/qualidade/quarentena/{id}/situacao")
        String alterarQuarentena(@PathVariable Long id) { return "ok"; }

        @GetMapping("/api/v1/linhagem/resultados")
        String linhagem() { return "ok"; }

        @GetMapping("/api/v1/qualidade/quarentena")
        String quarentena() { return "ok"; }

        @GetMapping("/api/v1/catalogo")
        String catalogo() { return "ok"; }

        @GetMapping("/api/arquivos/{id}/download")
        String download(@PathVariable Long id) { return "ok"; }

        @GetMapping("/api/rota-inexistente-na-matriz")
        String naoMapeada() { return "ok"; }
    }
}