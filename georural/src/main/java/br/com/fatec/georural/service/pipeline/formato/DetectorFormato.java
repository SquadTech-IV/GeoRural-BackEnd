package br.com.fatec.georural.service.pipeline.formato;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.geotools.referencing.CRS;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Component
public class DetectorFormato {

    private static final int BYTES_ASSINATURA = 64;
    private final Tika tika = new Tika();

    public FormatoDetectado detectar(byte[] conteudo, String nomeArquivo) {
        String extensao = extensao(nomeArquivo);
        String mime = detectarMime(conteudo, nomeArquivo);

        FormatoArquivo formato = porAssinatura(conteudo);
        if (formato == null) {
            formato = porExtensao(extensao, conteudo);
        }

        FormatoDetectado detectado = FormatoDetectado.de(formato, mime, extensao);

        // ZIP com .shp dentro e um shapefile
        if (formato == FormatoArquivo.ZIP && contemShapefile(conteudo)) {
            detectado = FormatoDetectado.de(FormatoArquivo.SHAPEFILE_ZIP, mime, extensao);
            Integer epsg = srsDoZip(conteudo);
            detectado = detectado.comSrs(epsg);
            if (epsg == null) {
                detectado = detectado.comObservacao(
                        "Shapefile sem .prj legivel: o sistema de referencia sera o do cadastro do conjunto.");
            }
        }

        if (formato == FormatoArquivo.CSV) {
            detectado = detectado.comObservacao(
                    "CSV nao carrega sistema de referencia: sera usado o do cadastro do conjunto.");
        }

        return detectado;
    }

    public FormatoDetectado detectar(InputStream entrada, String nomeArquivo) {
        try {
            return detectar(entrada.readAllBytes(), nomeArquivo);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel ler o arquivo para detectar o formato.", e);
        }
    }

    public String extensao(String nomeArquivo) {
        if (nomeArquivo == null || !nomeArquivo.contains(".")) {
            return "";
        }
        String ext = nomeArquivo.substring(nomeArquivo.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return "tiff".equals(ext) ? "tif" : ext;
    }

    // ---------------- deteccao ----------------

    /** Assinatura nos primeiros bytes. */
    private FormatoArquivo porAssinatura(byte[] c) {
        if (c == null || c.length < 8) {
            return null;
        }
        // ZIP: "PK\003\004"
        if (c[0] == 0x50 && c[1] == 0x4B && (c[2] == 0x03 || c[2] == 0x05 || c[2] == 0x07)) {
            return FormatoArquivo.ZIP;
        }
        // Shapefile .shp: file code 9994 big-endian
        if ((c[0] & 0xFF) == 0x00 && (c[1] & 0xFF) == 0x00
                && (c[2] & 0xFF) == 0x27 && (c[3] & 0xFF) == 0x0A) {
            return FormatoArquivo.SHAPEFILE;
        }
        // GeoPackage: SQLite "SQLite format 3\0"
        if (c.length >= 16 && new String(c, 0, 15, StandardCharsets.US_ASCII).equals("SQLite format 3")) {
            return FormatoArquivo.GEOPACKAGE;
        }
        // TIFF: II*\0 (little) ou MM\0* (big)
        if ((c[0] == 0x49 && c[1] == 0x49 && c[2] == 0x2A && c[3] == 0x00)
                || (c[0] == 0x4D && c[1] == 0x4D && c[2] == 0x00 && c[3] == 0x2A)) {
            return FormatoArquivo.GEOTIFF;
        }
        return null;
    }

    /** Sem assinatura clara: usa extensao e, para texto, olha o comeco do conteudo. */
    private FormatoArquivo porExtensao(String extensao, byte[] conteudo) {
        return switch (extensao) {
            case "shp" -> FormatoArquivo.SHAPEFILE;
            case "gpkg" -> FormatoArquivo.GEOPACKAGE;
            case "geojson" -> FormatoArquivo.GEOJSON;
            case "csv" -> FormatoArquivo.CSV;
            case "tif" -> FormatoArquivo.GEOTIFF;
            case "zip" -> FormatoArquivo.ZIP;
            case "json" -> pareceGeoJson(conteudo) ? FormatoArquivo.GEOJSON : FormatoArquivo.JSON;
            default -> textoPareceGeoJson(conteudo);
        };
    }

    /** .json com FeatureCollection dentro e GeoJSON. */
    private boolean pareceGeoJson(byte[] conteudo) {
        if (conteudo == null) return false;
        int limite = Math.min(conteudo.length, 2048);
        String inicio = new String(conteudo, 0, limite, StandardCharsets.UTF_8);
        return inicio.contains("FeatureCollection") || inicio.contains("\"geometry\"");
    }

    private FormatoArquivo textoPareceGeoJson(byte[] conteudo) {
        if (pareceGeoJson(conteudo)) {
            return FormatoArquivo.GEOJSON;
        }
        return FormatoArquivo.DESCONHECIDO;
    }

    private String detectarMime(byte[] conteudo, String nome) {
        try (InputStream in = new ByteArrayInputStream(
                conteudo.length > BYTES_ASSINATURA * 64
                        ? java.util.Arrays.copyOf(conteudo, BYTES_ASSINATURA * 64)
                        : conteudo)) {
            return tika.detect(in, nome);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    // ---------------- zip ----------------

    private boolean contemShapefile(byte[] zip) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entrada;
            while ((entrada = zis.getNextEntry()) != null) {
                if (!entrada.isDirectory()
                        && entrada.getName().toLowerCase(Locale.ROOT).endsWith(".shp")) {
                    return true;
                }
            }
        } catch (IOException e) {
            log.debug("Falha ao inspecionar zip em busca de .shp: {}", e.getMessage());
        }
        return false;
    }

    /** Le o primeiro .prj de dentro do zip e tenta descobrir o codigo EPSG. */
    private Integer srsDoZip(byte[] zip) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entrada;
            while ((entrada = zis.getNextEntry()) != null) {
                if (entrada.isDirectory()
                        || !entrada.getName().toLowerCase(Locale.ROOT).endsWith(".prj")) {
                    continue;
                }
                String wkt = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                return epsgDoWkt(wkt);
            }
        } catch (IOException e) {
            log.debug("Falha ao ler .prj do zip: {}", e.getMessage());
        }
        return null;
    }

    /** WKT -> codigo EPSG. Devolve null quando nao da para concluir */
    public Integer epsgDoWkt(String wkt) {
        if (wkt == null || wkt.isBlank()) {
            return null;
        }
        try {
            return CRS.lookupEpsgCode(CRS.parseWKT(wkt), true);
        } catch (Exception e) {
            log.debug("Nao foi possivel resolver o EPSG do .prj: {}", e.getMessage());
            return null;
        }
    }
}
