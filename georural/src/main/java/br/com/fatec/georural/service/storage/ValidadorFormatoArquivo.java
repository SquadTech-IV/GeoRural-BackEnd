package br.com.fatec.georural.service.storage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

import br.com.fatec.georural.exception.FormatoInvalidoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;

@Component
public class ValidadorFormatoArquivo {

    public static final String FORMATO_SHP = "SHP";
    public static final String FORMATO_GEOJSON = "GEOJSON";
    public static final String FORMATO_GPKG = "GPKG";
    public static final String FORMATO_CSV = "CSV";
    public static final String FORMATO_TIF = "TIF";

    private static final byte[] ZIP_MAGIC = { 0x50, 0x4B, 0x03, 0x04 };

    // zip interno maior que isso nao e aberto aqui; mesmo limite do inventario
    private static final long LIMITE_ZIP_INTERNO_BYTES = 4L * 1024 * 1024 * 1024;

    private static final byte[] GPKG_MAGIC = {
            'S', 'Q', 'L', 'i', 't', 'e', ' ', 'f', 'o', 'r', 'm', 'a', 't', ' ', '3', 0x00
    };

    private static final byte[] TIFF_LE = { 'I', 'I', '*', 0x00 };
    private static final byte[] TIFF_BE = { 'M', 'M', 0x00, '*' };
    private static final byte[] BIGTIFF_LE = { 'I', 'I', '+', 0x00 };
    private static final byte[] BIGTIFF_BE = { 'M', 'M', 0x00, '+' };

    private static final int CSV_BUFFER_SIZE = 8192;

    /**
     * Valida a extensão do nome do arquivo e retorna a sigla do formato correspondente.
     *
     * @param nomeArquivo nome do arquivo original
     * @return código do formato para persistência (SHP, GEOJSON, GPKG, CSV ou TIF)
     * @throws FormatoInvalidoException se a extensão não for aceita ou for .json
     */
    public String validarExtensao(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            throw new RequisicaoInvalidaException("Nome do arquivo não informado.");
        }
        String nomeLower = nomeArquivo.toLowerCase(Locale.ROOT);
        if (nomeLower.endsWith(".json")) {
            throw new FormatoInvalidoException(
                    "Formato .json não é aceito. Para arquivos GeoJSON utilize a extensão .geojson.");
        }
        if (nomeLower.endsWith(".zip")) {
            return FORMATO_SHP;
        }
        if (nomeLower.endsWith(".geojson")) {
            return FORMATO_GEOJSON;
        }
        if (nomeLower.endsWith(".gpkg")) {
            return FORMATO_GPKG;
        }
        if (nomeLower.endsWith(".csv")) {
            return FORMATO_CSV;
        }
        if (nomeLower.endsWith(".tif") || nomeLower.endsWith(".tiff")) {
            return FORMATO_TIF;
        }
        throw new FormatoInvalidoException(
                "Extensão de arquivo não suportada para o arquivo: " + nomeArquivo
                        + ". Extensões aceitas: .zip, .geojson, .gpkg, .csv, .tif, .tiff.");
    }

    /**
     * Valida a assinatura de conteúdo dos primeiros bytes do arquivo de acordo com o formato.
     *
     * @param arquivo arquivo salvo temporariamente em disco
     * @param formato código do formato retornado por {@link #validarExtensao(String)}
     * @throws FormatoInvalidoException se o conteúdo violar a assinatura esperada
     */
    public void validarConteudo(Path arquivo, String formato) {
        if (arquivo == null || !Files.exists(arquivo)) {
            throw new RequisicaoInvalidaException("Arquivo temporário não encontrado para validação.");
        }

        switch (formato) {
            case FORMATO_SHP -> validarZipShapefile(arquivo);
            case FORMATO_GPKG -> validarGeoPackage(arquivo);
            case FORMATO_TIF -> validarTiff(arquivo);
            case FORMATO_GEOJSON -> validarGeoJson(arquivo);
            case FORMATO_CSV -> validarCsv(arquivo);
            default -> throw new FormatoInvalidoException("Formato desconhecido: " + formato);
        }
    }

    private void validarZipShapefile(Path arquivo) {
        try (InputStream is = Files.newInputStream(arquivo)) {
            byte[] magic = is.readNBytes(4);
            if (magic.length < 4 || !Arrays.equals(magic, ZIP_MAGIC)) {
                throw new FormatoInvalidoException(
                        "Arquivo Shapefile inválido: assinatura mágica de arquivo ZIP não encontrada ('PK' 03 04).");
            }
        } catch (IOException e) {
            throw new FormatoInvalidoException("Falha ao ler assinatura do arquivo ZIP: " + e.getMessage());
        }

        try (ZipFile zipFile = new ZipFile(arquivo.toFile())) {
            if (temShapefileCompleto(nomesDoZip(zipFile))) {
                return;
            }

            // pacote por imovel do SICAR: zip de zips, um shapefile em cada zip interno
            if (temShapefileEmZipInterno(zipFile)) {
                return;
            }

            throw new FormatoInvalidoException(
                    "O arquivo .zip deve conter um shapefile com .shp, .shx e .dbf de mesmo nome base,"
                            + " soltos no zip ou dentro de um zip interno, como no pacote por imovel do SICAR.");
        } catch (ZipException e) {
            throw new FormatoInvalidoException("Arquivo .zip corrompido ou inválido: " + e.getMessage());
        } catch (IOException e) {
            throw new FormatoInvalidoException("Falha ao inspecionar o arquivo .zip: " + e.getMessage());
        }
    }

    // nomes das entradas de arquivo do zip, sem as pastas
    private List<String> nomesDoZip(ZipFile zipFile) {
        List<String> nomes = new ArrayList<>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory()) {
                nomes.add(entry.getName());
            }
        }
        return nomes;
    }

    // trio .shp, .shx e .dbf com o mesmo nome base
    private boolean temShapefileCompleto(List<String> nomes) {
        Set<String> shpBases = new HashSet<>();
        Set<String> shxBases = new HashSet<>();
        Set<String> dbfBases = new HashSet<>();

        for (String bruto : nomes) {
            String name = bruto.replace('\\', '/');
            if (name.startsWith("__MACOSX/") || name.contains("/__MACOSX/")) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".shp")) {
                shpBases.add(lower.substring(0, lower.length() - 4));
            } else if (lower.endsWith(".shx")) {
                shxBases.add(lower.substring(0, lower.length() - 4));
            } else if (lower.endsWith(".dbf")) {
                dbfBases.add(lower.substring(0, lower.length() - 4));
            }
        }

        for (String base : shpBases) {
            if (shxBases.contains(base) && dbfBases.contains(base)) {
                return true;
            }
        }
        return false;
    }

    // um nivel para dentro; o inventario e que percorre o pacote inteiro depois
    private boolean temShapefileEmZipInterno(ZipFile zipFile) throws IOException {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()
                    || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                continue;
            }
            if (entry.getSize() > LIMITE_ZIP_INTERNO_BYTES) {
                continue;
            }
            try (InputStream interno = zipFile.getInputStream(entry)) {
                if (temShapefileCompleto(nomesDoFluxoZip(interno))) {
                    return true;
                }
            } catch (ZipException e) {
                // zip interno ilegivel nao invalida o pacote: outro pode servir
                continue;
            }
        }
        return false;
    }

    // le so os nomes das entradas, sem descompactar o conteudo
    private List<String> nomesDoFluxoZip(InputStream entrada) throws IOException {
        List<String> nomes = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(entrada)) {
            ZipEntry interna;
            while ((interna = zis.getNextEntry()) != null) {
                if (!interna.isDirectory()) {
                    nomes.add(interna.getName());
                }
            }
        }
        return nomes;
    }

    private void validarGeoPackage(Path arquivo) {
        try (InputStream is = Files.newInputStream(arquivo)) {
            byte[] magic = is.readNBytes(GPKG_MAGIC.length);
            if (magic.length < GPKG_MAGIC.length || !Arrays.equals(magic, GPKG_MAGIC)) {
                throw new FormatoInvalidoException(
                        "Arquivo GeoPackage inválido: assinatura 'SQLite format 3\\0' não encontrada.");
            }
        } catch (IOException e) {
            throw new FormatoInvalidoException("Falha ao ler assinatura do GeoPackage: " + e.getMessage());
        }
    }

    private void validarTiff(Path arquivo) {
        try (InputStream is = Files.newInputStream(arquivo)) {
            byte[] magic = is.readNBytes(4);
            if (magic.length < 4 || (!Arrays.equals(magic, TIFF_LE)
                    && !Arrays.equals(magic, TIFF_BE)
                    && !Arrays.equals(magic, BIGTIFF_LE)
                    && !Arrays.equals(magic, BIGTIFF_BE))) {
                throw new FormatoInvalidoException(
                        "Arquivo TIFF inválido: assinatura TIFF não encontrada (esperado II*\\0, MM\\0*, II+\\0 ou MM\\0+).");
            }
        } catch (IOException e) {
            throw new FormatoInvalidoException("Falha ao ler assinatura do arquivo TIFF: " + e.getMessage());
        }
    }

    private void validarGeoJson(Path arquivo) {
        try (BufferedInputStream bis = new BufferedInputStream(Files.newInputStream(arquivo))) {
            bis.mark(4);
            byte[] bomCandidate = bis.readNBytes(3);
            boolean temBom = bomCandidate.length == 3
                    && (bomCandidate[0] & 0xFF) == 0xEF
                    && (bomCandidate[1] & 0xFF) == 0xBB
                    && (bomCandidate[2] & 0xFF) == 0xBF;

            if (!temBom) {
                bis.reset();
            }

            int b;
            int primeiroNaoBranco = -1;
            while ((b = bis.read()) != -1) {
                if (!Character.isWhitespace(b)) {
                    primeiroNaoBranco = b;
                    break;
                }
            }

            if (primeiroNaoBranco != '{') {
                throw new FormatoInvalidoException(
                        "Arquivo GeoJSON inválido: primeiro caractere não branco deve ser '{'.");
            }
        } catch (IOException e) {
            throw new FormatoInvalidoException("Falha ao ler arquivo GeoJSON: " + e.getMessage());
        }
    }

    private void validarCsv(Path arquivo) {
        try (InputStream is = Files.newInputStream(arquivo)) {
            byte[] buffer = is.readNBytes(CSV_BUFFER_SIZE);
            for (byte b : buffer) {
                if (b == 0x00) {
                    throw new FormatoInvalidoException(
                            "Arquivo CSV inválido: contém byte nulo nos primeiros 8 KB.");
                }
            }
        } catch (IOException e) {
            throw new FormatoInvalidoException("Falha ao ler arquivo CSV: " + e.getMessage());
        }
    }
}