package br.com.fatec.georural.service.pipeline.leitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Slf4j
@Component
public class ExtratorMembroZip {

    /** Pasta temporaria com os arquivos do membro. Quem chama deve apagar depois. */
    public record MembroExtraido(Path pasta, Path arquivoPrincipal, List<Path> partes) {

        public void apagar() {
            apagarArvore(pasta);
        }
    }

    public MembroExtraido extrair(Supplier<InputStream> zip, String caminhoInterno) {
        // zip aninhado: "externo.zip!/interno/AREA_IMOVEL.shp"
        int marca = caminhoInterno.indexOf("!/");
        if (marca >= 0) {
            Path internoEmDisco = copiarEntradaParaDisco(zip, caminhoInterno.substring(0, marca));
            try {
                return extrair(() -> abrir(internoEmDisco), caminhoInterno.substring(marca + 2));
            } finally {
                apagarArvore(internoEmDisco.getParent());
            }
        }

        String base = semExtensao(caminhoInterno);
        String extensaoAlvo = extensao(caminhoInterno);

        Path pasta = pastaTemporaria("georural-membro-");
        List<Path> partes = new ArrayList<>();
        Path principal = null;

        try (InputStream fonte = zip.get();
             ZipInputStream zis = new ZipInputStream(fonte)) {
            ZipEntry entrada;
            while ((entrada = zis.getNextEntry()) != null) {
                if (entrada.isDirectory() || !semExtensao(entrada.getName()).equals(base)) {
                    continue;
                }
                Path destino = pasta.resolve(nomeSimples(entrada.getName()));
                Files.copy(zis, destino);
                partes.add(destino);
                if (extensao(entrada.getName()).equals(extensaoAlvo)) {
                    principal = destino;
                }
            }
        } catch (IOException e) {
            apagarArvore(pasta);
            throw new IllegalStateException("Falha ao extrair '" + caminhoInterno + "' do zip.", e);
        }

        if (principal == null) {
            apagarArvore(pasta);
            throw new IllegalStateException("Membro nao encontrado no zip: " + caminhoInterno);
        }
        log.debug("Membro '{}' extraido com {} parte(s).", caminhoInterno, partes.size());
        return new MembroExtraido(pasta, principal, partes);
    }

    /** Procura no zip o primeiro arquivo com a extensao dada e devolve o caminho interno dele. */
    public String primeiroComExtensao(Supplier<InputStream> zip, String extensaoProcurada) {
        String alvo = "." + extensaoProcurada.toLowerCase(Locale.ROOT);
        try (InputStream fonte = zip.get();
             ZipInputStream zis = new ZipInputStream(fonte)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().toLowerCase(Locale.ROOT).endsWith(alvo)) {
                    return e.getName();
                }
            }
        } catch (IOException ex) {
            log.debug("Falha ao procurar '{}' no zip: {}", alvo, ex.getMessage());
        }
        return null;
    }

    /** Copia UMA entrada do zip para o disco temporario, em fluxo. */
    private Path copiarEntradaParaDisco(Supplier<InputStream> zip, String nomeEntrada) {
        Path pasta = pastaTemporaria("georural-aninhado-");
        try (InputStream fonte = zip.get();
             ZipInputStream zis = new ZipInputStream(fonte)) {
            ZipEntry entrada;
            while ((entrada = zis.getNextEntry()) != null) {
                if (!entrada.isDirectory() && entrada.getName().equals(nomeEntrada)) {
                    Path destino = pasta.resolve(nomeSimples(nomeEntrada));
                    Files.copy(zis, destino);
                    return destino;
                }
            }
        } catch (IOException e) {
            apagarArvore(pasta);
            throw new IllegalStateException("Falha ao ler zip aninhado: " + nomeEntrada, e);
        }
        apagarArvore(pasta);
        throw new IllegalStateException("Zip aninhado nao encontrado: " + nomeEntrada);
    }

    private static InputStream abrir(Path arquivo) {
        try {
            return Files.newInputStream(arquivo);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao abrir arquivo temporario: " + arquivo, e);
        }
    }

    private Path pastaTemporaria(String prefixo) {
        try {
            return Files.createTempDirectory(prefixo);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel criar pasta temporaria.", e);
        }
    }

    private static void apagarArvore(Path raiz) {
        if (raiz == null) {
            return;
        }
        try (var fluxo = Files.walk(raiz)) {
            fluxo.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                 .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
        } catch (IOException ignored) {
            // pasta temporaria: falha na limpeza nao afeta a leitura
        }
    }

    private String semExtensao(String caminho) {
        int p = caminho.lastIndexOf('.');
        return p < 0 ? caminho : caminho.substring(0, p);
    }

    private String extensao(String caminho) {
        int p = caminho.lastIndexOf('.');
        return p < 0 ? "" : caminho.substring(p + 1).toLowerCase(Locale.ROOT);
    }

    private String nomeSimples(String caminho) {
        int barra = Math.max(caminho.lastIndexOf('/'), caminho.lastIndexOf('\\'));
        return barra < 0 ? caminho : caminho.substring(barra + 1);
    }
}
