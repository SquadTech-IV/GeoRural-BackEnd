package br.com.fatec.georural.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "georural.lake")
public record DataLakeProperties(
        String region,
        String namespace,
        Bucket bucket,
        String ociConfigFile,
        String ociProfile
) {

    public record Bucket(
            String bruta,
            String quarentena,
            String tratada,
            String publicada
    ) {}
}