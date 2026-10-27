package br.com.fatec.georural.service.treatment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Component
public class PreparadorArquivoProcessamento {

    private static final Logger log = LoggerFactory.getLogger(PreparadorArquivoProcessamento.class);

    /** Arquivo pronto para o detector e o diretorio temporario a ser apagado depois. */
    public record Preparacao(File arquivo, Path diretorio) {}

    public Preparacao preparar(byte[] conteudo, String nomeOriginal) {
        if (conteudo == null || conteudo.length == 0) {
            throw new IllegalArgumentException("Arquivo sem conteudo; nao e possivel preparar para processamento.");
        }
        String ext = extensao(nomeOriginal);
        Path dir;
        try {
            dir = Files.createTempDirectory("georural-proc-");
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao criar diretorio temporario de processamento.", e);
        }

        try {
            if ("zip".equals(ext)) {
                extrairZip(conteudo, dir);
                File shp = localizarShapefile(dir);
                if (shp == null) {
                    throw new IllegalArgumentException(
                            "O .zip nao contem um shapefile (.shp) com seus arquivos acompanhantes (.dbf/.shx).");
                }
                log.info("Arquivo .zip preparado: shapefile {} em {}", shp.getName(), dir);
                return new Preparacao(shp, dir);
            }

            String nome = "dado." + (ext.isBlank() ? "dat" : ext);
            Path destino = dir.resolve(nome);
            Files.write(destino, conteudo);
            log.info("Arquivo preparado: {} em {}", nome, dir);
            return new Preparacao(destino.toFile(), dir);

        } catch (RuntimeException e) {
            limpar(dir);
            throw e;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Apaga o diretorio temporario (recursivo). Nunca lanca. */
    public void limpar(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignorado) {
                    // diretorio temporario; falha ao apagar nao afeta o resultado
                }
            });
        } catch (IOException ignorado) {
            // idem
        }
    }

    private static String extensao(String nome) {
        if (nome == null) {
            return "";
        }
        int p = nome.lastIndexOf('.');
        return (p >= 0 && p < nome.length() - 1) ? nome.substring(p + 1).toLowerCase() : "";
    }

    private static void extrairZip(byte[] conteudo, Path dir) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(conteudo))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path destino = dir.resolve(entry.getName()).normalize();
                // defesa contra zip-slip: ignora entradas que escapariam do diretorio
                if (!destino.startsWith(dir)) {
                    zis.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destino);
                } else {
                    Files.createDirectories(destino.getParent());
                    Files.copy(zis, destino, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao descompactar o arquivo .zip.", e);
        }
    }

    private static File localizarShapefile(Path dir) {
        try (var paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".shp"))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao varrer os arquivos extraidos do .zip.", e);
        }
    }
}
