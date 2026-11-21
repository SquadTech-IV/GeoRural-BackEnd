package br.com.fatec.georural.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AuditoriaWebConfig implements WebMvcConfigurer {

    private final AuditoriaAcaoInterceptor auditoriaAcaoInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditoriaAcaoInterceptor).addPathPatterns("/api/**");
    }
}
