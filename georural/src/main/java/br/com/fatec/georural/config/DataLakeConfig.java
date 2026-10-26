package br.com.fatec.georural.config;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(DataLakeProperties.class)
public class DataLakeConfig {

    @Bean
    @Lazy
    public ObjectStorage objectStorage(DataLakeProperties props) throws IOException {

        ConfigFileReader.ConfigFile config =
                ConfigFileReader.parse(props.ociConfigFile(), props.ociProfile());

        ConfigFileAuthenticationDetailsProvider credenciais =
                new ConfigFileAuthenticationDetailsProvider(config);

        return ObjectStorageClient.builder()
                .region(Region.fromRegionId(props.region()))
                .build(credenciais);
    }
}