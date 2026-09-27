package br.com.fatec.georural.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IdentificadorFormatoService {

    private final Tika tika = new Tika();
    

    private static final Map<String, Set<String>> MIMES_POR_EXTENSAO = Map.of(
            "csv",     Set.of("text/csv", "text/plain"),
            "geojson", Set.of("application/json", "text/plain", "application/geo+json"),
            "shp",     Set.of("application/x-shapefile", "application/octet-stream"),
            "gpkg",    Set.of("application/octet-stream", "application/x-sqlite3",  "application/geopackage+sqlite3"),
            "json",     Set.of("application/json", "text/plain"),
            "zip",     Set.of("application/zip", "application/x-zip-compressed", "application/octet-stream"),
            "tif",     Set.of("image/tiff")
    );
    
    private static final Set<String> MIMES_GENERICOS = Set.of(
        "application/octet-stream", "text/plain"
    );

    public String extrairExtensao(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename();
        if (nome == null || !nome.contains(".")) {
            return "";
        }
        String ext = nome.substring(nome.lastIndexOf('.') + 1).toLowerCase();
        return ext.equals("tiff") ? "tif" : ext;

    }

    public String detectarMimeType(MultipartFile arquivo) {
        try {
            return tika.detect(arquivo.getInputStream(), arquivo.getOriginalFilename());
        } catch (IOException e) {
            return "erro";
        }
    }

    public boolean isFormatoAceito(MultipartFile arquivo) {
        String extensao = extrairExtensao(arquivo);
        Set<String> mimesEsperados = MIMES_POR_EXTENSAO.get(extensao);
        if (mimesEsperados == null) {
            return false;
        }
        return mimesEsperados.contains(detectarMimeType(arquivo));
    }
}
