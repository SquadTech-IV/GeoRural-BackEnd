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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class PreparadorArquivoProcessamento {

    private static final Logger log = LoggerFactory.getLogger(PreparadorArquivoProcessamento.class);

    private static final int PROFUNDIDADE_MAX_ZIP = 5;

    /**
     * Palavras-chave que identificam o tema de cada tipo de conjunto no nome da pasta/arquivo
     * (comparadas sem acento, minusculas, so alfanumerico). Ex.: tipo IMOVEL casa com "area_do_imovel".
     */
    private static final Map<String, List<String>> TEMAS = Map.of(
            "IMOVEL",           List.of("areadoimovel", "areaimovel", "imovel"),
            "RESERVA_LEGAL",    List.of("reservalegal", "reserva"),
            "EMBARGO",          List.of("embargo"),
            "APP",              List.of("preservacaopermanente", "app"),
            "VEGETACAO",        List.of("coberturadosolo", "vegetacao", "cobertura"),
            "AREAS_PROTEGIDAS", List.of("areasprotegidas", "areaprotegida", "unidadeconservacao"),
            "DESMATAMENTO",     List.of("desmatamento", "prodes"),
            "FOCOS",            List.of("focos", "queimada", "calor"));

    /** Arquivo pronto para o detector e o diretorio temporario a ser apagado depois. */
    public record Preparacao(File arquivo, Path diretorio) {}

    /** Compatibilidade: sem dica de tema (usa o unico shapefile encontrado). */
    public Preparacao preparar(byte[] conteudo, String nomeOriginal) {
        return preparar(conteudo, nomeOriginal, null);
    }

    /**
     * @param tipoConjunto codigo do tipo do conjunto (IMOVEL, RESERVA_LEGAL, EMBARGO...); pode ser null.
     */
    public Preparacao preparar(byte[] conteudo, String nomeOriginal, String tipoConjunto) {
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
                extrairZipRecursivo(conteudo, dir, 0);
                File escolhido = escolher(localizarShapefiles(dir), tipoConjunto);
                log.info("Zip preparado: shapefile '{}' (tipo do conjunto: {}) em {}",
                        escolhido.getName(), tipoConjunto, dir);
                return new Preparacao(escolhido, dir);
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
            limpar(dir);
            throw new UncheckedIOException("Falha ao preparar o arquivo para processamento.", e);
        }
    }

    /** Escolhe o shapefile: unico -> usa; varios -> casa pelo tipo do conjunto; senao, erro claro. */
    private static File escolher(List<File> shapefiles, String tipoConjunto) {
        if (shapefiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "O arquivo nao contem nenhum shapefile (.shp) com seus acompanhantes (.dbf/.shx).");
        }
        if (shapefiles.size() == 1) {
            return shapefiles.get(0);
        }
        List<String> chaves = (tipoConjunto == null) ? List.of()
                : TEMAS.getOrDefault(tipoConjunto.trim().toUpperCase(Locale.ROOT), List.of());
        if (!chaves.isEmpty()) {
            for (File shp : shapefiles) {
                String alvo = normalizar(shp.getPath());
                for (String chave : chaves) {
                    if (alvo.contains(chave)) {
                        return shp;
                    }
                }
            }
        }
        List<String> nomes = shapefiles.stream().map(File::getName).sorted().toList();
        throw new IllegalArgumentException(
                "O pacote tem varios shapefiles " + nomes + " e nao deu para identificar qual usar"
                        + (tipoConjunto == null
                        ? " (conjunto sem tipo informado)."
                        : " para o tipo de conjunto '" + tipoConjunto + "'. Envie o zip especifico desse tema."));
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
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
        return (p >= 0 && p < nome.length() - 1) ? nome.substring(p + 1).toLowerCase(Locale.ROOT) : "";
    }

    /** Extrai o zip; se encontrar outro .zip dentro, extrai esse tambem (ate PROFUNDIDADE_MAX_ZIP). */
    private static void extrairZipRecursivo(byte[] conteudo, Path dir, int profundidade) {
        if (profundidade > PROFUNDIDADE_MAX_ZIP) {
            throw new IllegalArgumentException("Zip aninhado profundo demais.");
        }
        List<Path> zipsInternos = new ArrayList<>();
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
                    if (destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        zipsInternos.add(destino);
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao descompactar o arquivo .zip.", e);
        }
        // extrai cada zip interno numa subpasta (os acompanhantes ficam juntos do .shp) e remove o .zip
        for (Path zipInterno : zipsInternos) {
            try {
                byte[] bytesInternos = Files.readAllBytes(zipInterno);
                Path subdir = zipInterno.resolveSibling(zipInterno.getFileName().toString() + "_extraido");
                Files.createDirectories(subdir);
                extrairZipRecursivo(bytesInternos, subdir, profundidade + 1);
                Files.deleteIfExists(zipInterno);
            } catch (IOException e) {
                throw new UncheckedIOException("Falha ao descompactar zip interno: " + zipInterno.getFileName(), e);
            }
        }
    }

    private static List<File> localizarShapefiles(Path dir) {
        try (var paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".shp"))
                    .map(Path::toFile)
                    .sorted(Comparator.comparing(File::getName))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao varrer os arquivos extraidos do .zip.", e);
        }
    }
}
