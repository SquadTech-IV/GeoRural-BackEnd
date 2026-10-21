package br.com.fatec.georural.service.storage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.springframework.stereotype.Component;

import br.com.fatec.georural.exception.RequisicaoInvalidaException;

/**
 * Valida a extensão e a assinatura de conteúdo dos arquivos enviados.
 *
 * <p>Formatos suportados e seus valores gravados em {@code arq_formato}:
 * <ul>
 *   <li>{@code .zip} -> SHP (Shapefile; deve conter .shp, .shx e .dbf com o mesmo nome base)</li>
 *   <li>{@code .geojson} -> GEOJSON (primeiro caractere não branco '{', tolerando BOM UTF-8)</li>
 *   <li>{@code .gpkg} -> GPKG (assinatura 'SQLite format 3\0')</li>
 *   <li>{@code .csv} -> CSV (sem byte nulo nos primeiros 8 KB)</li>
 *   <li>{@code .tif}, {@code .tiff} -> TIF (assinaturas clássica ou BigTIFF, little ou big-endian)</li>
 * </ul>
 *
 * <p>Arquivos com extensão {@code .json} não são aceitos.
 */
@Component
public class ValidadorFormatoArquivo {

    public static final String FORMATO_SHP = "SHP";
    public static final String FORMATO_GEOJSON = "GEOJSON";
    public static final String FORMATO_GPKG = "GPKG";
    public static final String FORMATO_CSV = "CSV";
    public static final String FORMATO_TIF = "TIF";

    private static final byte[] ZIP_MAGIC = { 0x50, 0x4B, 0x03, 0x04 };

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
     * @throws RequisicaoInvalidaException se a extensão não for aceita ou for .json
     */
    public String validarExtensao(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            throw new RequisicaoInvalidaException("Nome do arquivo não informado.");
        }
        String nomeLower = nomeArquivo.toLowerCase(Locale.ROOT);
        if (nomeLower.endsWith(".json")) {
            throw new RequisicaoInvalidaException(
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
        throw new RequisicaoInvalidaException(
                "Extensão de arquivo não suportada para o arquivo: " + nomeArquivo
                        + ". Extensões aceitas: .zip, .geojson, .gpkg, .csv, .tif, .tiff.");
    }

    /**
     * Valida a assinatura de conteúdo dos primeiros bytes do arquivo de acordo com o formato.
     *
     * @param arquivo arquivo salvo temporariamente em disco
     * @param formato código do formato retornado por {@link #validarExtensao(String)}
     * @throws RequisicaoInvalidaException se o conteúdo violar a assinatura esperada
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
            default -> throw new RequisicaoInvalidaException("Formato desconhecido: " + formato);
        }
    }

    private void validarZipShapefile(Path arquivo) {
        try (InputStream is = Files.newInputStream(arquivo)) {
            byte[] magic = is.readNBytes(4);
            if (magic.length < 4 || !Arrays.equals(magic, ZIP_MAGIC)) {
                throw new RequisicaoInvalidaException(
                        "Arquivo Shapefile inválido: assinatura mágica de arquivo ZIP não encontrada ('PK' 03 04).");
            }
        } catch (IOException e) {
            throw new RequisicaoInvalidaException("Falha ao ler assinatura do arquivo ZIP: " + e.getMessage());
        }

        try (ZipFile zipFile = new ZipFile(arquivo.toFile())) {
            Set<String> shpBases = new HashSet<>();
            Set<String> shxBases = new HashSet<>();
            Set<String> dbfBases = new HashSet<>();

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
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

            boolean completo = false;
            for (String base : shpBases) {
                if (shxBases.contains(base) && dbfBases.contains(base)) {
                    completo = true;
                    break;
                }
            }

            if (!completo) {
                throw new RequisicaoInvalidaException(
                        "O arquivo .zip deve ser um shapefile contendo ao menos os arquivos .shp, .shx e .dbf com o mesmo nome base.");
            }
        } catch (ZipException e) {
            throw new RequisicaoInvalidaException("Arquivo .zip corrompido ou inválido: " + e.getMessage());
        } catch (IOException e) {
            throw new RequisicaoInvalidaException("Falha ao inspecionar o arquivo .zip: " + e.getMessage());
        }
    }

    private void validarGeoPackage(Path arquivo) {
        try (InputStream is = Files.newInputStream(arquivo)) {
            byte[] magic = is.readNBytes(GPKG_MAGIC.length);
            if (magic.length < GPKG_MAGIC.length || !Arrays.equals(magic, GPKG_MAGIC)) {
                throw new RequisicaoInvalidaException(
                        "Arquivo GeoPackage inválido: assinatura 'SQLite format 3\\0' não encontrada.");
            }
        } catch (IOException e) {
            throw new RequisicaoInvalidaException("Falha ao ler assinatura do GeoPackage: " + e.getMessage());
        }
    }

    private void validarTiff(Path arquivo) {
        try (InputStream is = Files.newInputStream(arquivo)) {
            byte[] magic = is.readNBytes(4);
            if (magic.length < 4 || (!Arrays.equals(magic, TIFF_LE)
                    && !Arrays.equals(magic, TIFF_BE)
                    && !Arrays.equals(magic, BIGTIFF_LE)
                    && !Arrays.equals(magic, BIGTIFF_BE))) {
                throw new RequisicaoInvalidaException(
                        "Arquivo TIFF inválido: assinatura TIFF não encontrada (esperado II*\\0, MM\\0*, II+\\0 ou MM\\0+).");
            }
        } catch (IOException e) {
            throw new RequisicaoInvalidaException("Falha ao ler assinatura do arquivo TIFF: " + e.getMessage());
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
                throw new RequisicaoInvalidaException(
                        "Arquivo GeoJSON inválido: primeiro caractere não branco deve ser '{'.");
            }
        } catch (IOException e) {
            throw new RequisicaoInvalidaException("Falha ao ler arquivo GeoJSON: " + e.getMessage());
        }
    }

    private void validarCsv(Path arquivo) {
        try (InputStream is = Files.newInputStream(arquivo)) {
            byte[] buffer = is.readNBytes(CSV_BUFFER_SIZE);
            for (byte b : buffer) {
                if (b == 0x00) {
                    throw new RequisicaoInvalidaException(
                            "Arquivo CSV inválido: contém byte nulo nos primeiros 8 KB.");
                }
            }
        } catch (IOException e) {
            throw new RequisicaoInvalidaException("Falha ao ler arquivo CSV: " + e.getMessage());
        }
    }
}
