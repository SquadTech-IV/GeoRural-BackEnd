package br.com.fatec.georural.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final List<String> METODOS = List.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");

    // Origens liberadas; padrao e a do Vite em desenvolvimento.
    private final List<String> origens;

    public CorsConfig(
            @Value("${georural.cors.origens:http://localhost:5173}")
            List<String> origens) {
        this.origens = origens;
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origens.toArray(String[]::new))
                .allowedMethods(METODOS.toArray(String[]::new))
                .allowedHeaders("*");

        // A documentacao tambem precisa, para quem abre o Swagger de outra origem.
        registry.addMapping("/v3/api-docs/**")
                .allowedOrigins(origens.toArray(String[]::new))
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("*");
    }

    // O Spring Security le o CORS daqui, nao do addCorsMappings.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(origens);
        config.setAllowedMethods(METODOS);
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource fonte =
                new UrlBasedCorsConfigurationSource();

        fonte.registerCorsConfiguration("/api/**", config);
        fonte.registerCorsConfiguration("/v3/api-docs/**", config);

        return fonte;
    }
}
