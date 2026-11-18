package br.com.fatec.georural.config;

import br.com.fatec.georural.security.KeycloakJwtAuthenticationConverter;
import br.com.fatec.georural.security.SegurancaExceptionHandlers;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import br.com.fatec.georural.security.ValidacaoUsuarioJwtFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    // Rotas abertas mesmo com a autenticacao ligada.
    private static final String[] ABERTOS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/actuator/health",
            "/actuator/health/**"
    };

    private static final String[] TODOS_PERFIS = {
            "ADMIN", "GESTOR", "OPERADOR", "ANALISTA", "AUDITOR"
    };

    private static final String[] QUALIDADE_E_LINHAGEM = {
            "ADMIN", "GESTOR", "OPERADOR", "AUDITOR"
    };

    private static final String[] OPERACAO_E_INGESTAO = {
            "ADMIN", "GESTOR", "OPERADOR"
    };

    private static final String[] GESTAO = {
            "ADMIN", "GESTOR"
    };

    private final KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;
    private final SegurancaExceptionHandlers segurancaExceptionHandlers;
    private final ValidacaoUsuarioJwtFilter validacaoUsuarioJwtFilter;

    // true: API sem autenticacao (contingencia). false: exige JWT do Keycloak fora das rotas acima.
    @Value("${georural.seguranca.liberar-api:false}")
    private boolean liberarApi;

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        if (liberarApi) {
            log.warn("""
                    ATENCAO: georural.seguranca.liberar-api=true.
                    A API esta SEM autenticacao - qualquer um que alcance a porta chama qualquer rota.
                    Para ativar o Keycloak, defina georural.seguranca.liberar-api=false.
                    """);

            http.authorizeHttpRequests(regras -> regras.anyRequest().permitAll());
            return http.build();
        }

        log.info("Seguranca Keycloak ativa: matriz de permissoes aplicada em todas as rotas.");

        http.authorizeHttpRequests(regras -> regras
                        // 1. Preflight e rotas publicas de documentacao/health
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(ABERTOS).permitAll()

                        // 2. Rotas restritas exclusivamente a ADMIN
                        .requestMatchers("/api/teste/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/pipeline/limite-territorial/recarregar").hasRole("ADMIN")

                        // 3. Gestao e administracao (ADMIN, GESTOR)
                        .requestMatchers(HttpMethod.POST, "/api/fontes").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.PUT, "/api/fontes/**").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.DELETE, "/api/fontes/**").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.PATCH, "/api/fontes/**").hasAnyRole(GESTAO)

                        .requestMatchers(HttpMethod.DELETE, "/api/conjuntos/**").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.PATCH, "/api/conjuntos/**").hasAnyRole(GESTAO)

                        .requestMatchers(HttpMethod.POST, "/api/versoes/*/publicar").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.POST, "/api/versoes/*/reprovar").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.POST, "/api/versoes/*/reverter").hasAnyRole(GESTAO)

                        .requestMatchers(HttpMethod.POST, "/api/indicadores/**").hasAnyRole(GESTAO)
                        .requestMatchers(HttpMethod.PUT, "/api/imoveis/*/nome").hasAnyRole(GESTAO)

                        // 4. Escrita de conjuntos, ingestao, uploads e execucao/reprocessamento de pipeline (ADMIN, GESTOR, OPERADOR)
                        .requestMatchers(HttpMethod.POST, "/api/conjuntos").hasAnyRole(OPERACAO_E_INGESTAO)
                        .requestMatchers(HttpMethod.PUT, "/api/conjuntos/**").hasAnyRole(OPERACAO_E_INGESTAO)

                        .requestMatchers(HttpMethod.POST, "/api/ingestao/**").hasAnyRole(OPERACAO_E_INGESTAO)
                        .requestMatchers(HttpMethod.POST, "/api/arquivos/brutos/upload").hasAnyRole(OPERACAO_E_INGESTAO)
                        .requestMatchers(HttpMethod.POST, "/api/arquivos/*/processar").hasAnyRole(OPERACAO_E_INGESTAO)

                        .requestMatchers(HttpMethod.POST, "/api/pipeline/executar").hasAnyRole(OPERACAO_E_INGESTAO)
                        .requestMatchers(HttpMethod.POST, "/api/pipeline/lote").hasAnyRole(OPERACAO_E_INGESTAO)
                        .requestMatchers(HttpMethod.POST, "/api/pipeline/etapas/**").hasAnyRole(OPERACAO_E_INGESTAO)
                        .requestMatchers(HttpMethod.POST, "/api/pipeline/execucoes/*/etapas/*/reexecutar").hasAnyRole(OPERACAO_E_INGESTAO)

                        .requestMatchers(HttpMethod.PATCH, "/api/v1/qualidade/**").hasAnyRole(OPERACAO_E_INGESTAO)

                        // 5. Linhagem e qualidade (ADMIN, GESTOR, OPERADOR, AUDITOR - bloqueia ANALISTA)
                        .requestMatchers(HttpMethod.GET, "/api/v1/linhagem/**").hasAnyRole(QUALIDADE_E_LINHAGEM)
                        .requestMatchers(HttpMethod.GET, "/api/linhagem/**").hasAnyRole(QUALIDADE_E_LINHAGEM)
                        .requestMatchers(HttpMethod.GET, "/api/v1/qualidade/**").hasAnyRole(QUALIDADE_E_LINHAGEM)
                        .requestMatchers(HttpMethod.GET, "/api/execucoes/*/qualidade").hasAnyRole(QUALIDADE_E_LINHAGEM)

                        // 6. Consultas gerais, downloads e leitura de execucoes (todos os 5 perfis autenticados)
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalogo/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/v1/indicadores/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/indicadores/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/imoveis/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/mapa/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/arquivos/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/inventario/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/perfis/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/datalake/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/conjuntos/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/fontes/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/versoes/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/trajetoria-conjuntos/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/zona-tratada/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/ingestao/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/v1/execucoes/**").hasAnyRole(TODOS_PERFIS)
                        .requestMatchers(HttpMethod.GET, "/api/pipeline/**").hasAnyRole(TODOS_PERFIS)

                        // 7. Negar por padrao qualquer rota nao mapeada na matriz
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(segurancaExceptionHandlers)
                        .accessDeniedHandler(segurancaExceptionHandlers))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(segurancaExceptionHandlers)
                        .accessDeniedHandler(segurancaExceptionHandlers)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                        .addFilterAfter(validacaoUsuarioJwtFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }
}