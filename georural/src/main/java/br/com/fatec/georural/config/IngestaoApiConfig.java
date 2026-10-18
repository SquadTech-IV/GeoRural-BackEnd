package br.com.fatec.georural.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class IngestaoApiConfig {

    @Bean
    public RestClient restClientIngestao() {
        return RestClient.builder().build();
    }
}