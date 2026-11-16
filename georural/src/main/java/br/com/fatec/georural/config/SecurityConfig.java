package br.com.fatec.georural.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(
        name = "georural.seguranca.provisoria",
        havingValue = "true",
        matchIfMissing = true
)
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

    // true: API sem autenticacao nenhuma. false: exige JWT fora das rotas acima.
    @Value("${georural.seguranca.liberar-api:true}")
    private boolean liberarApi;

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http) throws Exception {
        // API com token no cabecalho: nao usa sessao nem formulario, entao CSRF nao se aplica.
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
                    E o que permite o Airflow (US#10) rodar antes da US#13 entrar.
                    Quando o Keycloak estiver de pe, ponha georural.seguranca.liberar-api=false.
                    """);

            // Sem oauth2ResourceServer: a aplicacao sobe sem o Keycloak responder.
            http.authorizeHttpRequests(regras -> regras.anyRequest().permitAll());

            return http.build();
        }

        log.info("Autenticacao ligada: JWT do Keycloak exigido fora das rotas de documentacao.");

        http.authorizeHttpRequests(regras -> regras
                        // O preflight do navegador vem sem token.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(ABERTOS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));

        return http.build();
    }
}