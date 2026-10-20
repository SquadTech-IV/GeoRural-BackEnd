package br.com.fatec.georural.service.ingestao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ArmazenamentoZonaBrutaLocal implements ArmazenamentoZonaBruta {

    private final Path base;

    public ArmazenamentoZonaBrutaLocal(
            @Value("${georural.datalake.bruta:datalake/bruta}") String base) {
        this.base = Path.of(base);
    }

    @Override
    public String salvar(Long conjuntoId, String nomeArquivo, byte[] conteudo) {
        try {
            Path dir = base.resolve("conjunto-" + conjuntoId);
            Files.createDirectories(dir);
            Path destino = dir.resolve(sanitizar(nomeArquivo));
            Files.write(destino, conteudo == null ? new byte[0] : conteudo);
            return destino.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar o arquivo na zona bruta: " + nomeArquivo, e);
        }
    }

    private static String sanitizar(String nome) {
        String n = (nome == null || nome.isBlank()) ? "arquivo" : nome;
        return n.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}