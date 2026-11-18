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

    private final KeycloakJwtAuthenticationConverter jwtAuthenticationConverter;
    private final SegurancaExceptionHandlers segurancaExceptionHandlers;

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

        log.info("Seguranca Keycloak ativa: JWT exigido fora das rotas de documentacao e health.");

        http.authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(ABERTOS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(segurancaExceptionHandlers)
                        .accessDeniedHandler(segurancaExceptionHandlers))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(segurancaExceptionHandlers)
                        .accessDeniedHandler(segurancaExceptionHandlers)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}