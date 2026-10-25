package br.com.fatec.georural.service.treatment.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementação em disco. Substitua por uma implementação MinIO/S3 quando o
 * object storage estiver definido (basta outra classe que implemente
 * ZonaTratadaStorage e remover o @Component desta).
 */
@Component
@ConditionalOnProperty(name = "georural.zona-tratada.storage", havingValue = "local", matchIfMissing = true)
public class ZonaTratadaStorageLocal implements ZonaTratadaStorage {

    @Value("${georural.zona-tratada.base-dir:./zona-tratada}")
    private String baseDir;

    @Override
    public String gravar(String chave, byte[] conteudo) {

        try {
            Path destino = Path.of(baseDir, chave);

            Files.createDirectories(destino.getParent());

            Files.write(destino, conteudo);

            return destino.toString();

        } catch (IOException e) {
            throw new RuntimeException(
                    "Erro ao gravar na zona tratada: " + e.getMessage(),
                    e);
        }
    }
}
