package br.com.fatec.georural.service.treatment.core;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.service.storage.ArmazenamentoZonaPort;
import br.com.fatec.georural.service.storage.Zona;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geojson.feature.FeatureJSON;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Serviço responsável pela leitura em fluxo (streaming, registro a registro) 
 * de arquivos contidos na Zona Bruta do Data Lake (US#08 - T04).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeitorFluxoZonaBrutaService {

    private final ArmazenamentoZonaPort armazenamentoZonaPort;

    /**
     * Processa um arquivo da zona bruta registro a registro, invocando o consumidor para cada feição.
     * Não carrega toda a coleção de feições em memória de uma só vez.
     *
     * @param arquivoBruto Entidade do arquivo bruto a ser lido.
     * @param consumidor   Ação executada para cada SimpleFeature lido.
     */
    public void processarEmFluxo(ArquivoBruto arquivoBruto, Consumer<SimpleFeature> consumidor) {
        Objects.requireNonNull(arquivoBruto, "Arquivo bruto não pode ser nulo.");
        Objects.requireNonNull(consumidor, "Consumidor de feições não pode ser nulo.");

        String caminho = arquivoBruto.getPathArmazenamento();
        String formato = arquivoBruto.getFormato() != null ? arquivoBruto.getFormato().toUpperCase() : "";

        log.info("Iniciando leitura em fluxo do arquivo id={}, caminho={}, formato={}", 
                arquivoBruto.getId(), caminho, formato);

        try (InputStream is = armazenamentoZonaPort.ler(Zona.BRUTA, caminho)) {
            if ("GEOJSON".contains(formato) || caminho.endsWith(".json") || caminho.endsWith(".geojson")) {
                lerGeoJsonEmFluxo(is, consumidor);
            } else if ("ZIP".equalsIgnoreCase(formato) || caminho.endsWith(".zip")) {
                lerZipShapefileEmFluxo(is, consumidor);
            } else {
                lerViaDataStoreTemporario(is, caminho, consumidor);
            }
        } catch (Exception e) {
            log.error("Erro ao ler arquivo em fluxo da zona bruta: {}", caminho, e);
            throw new RuntimeException("Falha ao ler arquivo em fluxo da zona bruta: " + caminho, e);
        }
    }

    private void lerGeoJsonEmFluxo(InputStream is, Consumer<SimpleFeature> consumidor) throws IOException {
        FeatureJSON featureJSON = new FeatureJSON();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
            try (var iterator = featureJSON.streamFeatureCollection(reader)) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = (SimpleFeature) iterator.next();
                    consumidor.accept(feature);
                }
            }
        }
    }

    private void lerZipShapefileEmFluxo(InputStream is, Consumer<SimpleFeature> consumidor) throws IOException {
        Path tempDir = Files.createTempDirectory("georural-zip-stream-");
        try {
            Path shpPath = descompactarZipEEncontrarShp(is, tempDir);
            if (shpPath == null) {
                throw new IllegalArgumentException("Nenhum arquivo .shp encontrado dentro do arquivo ZIP.");
            }
            lerShapefileLocalEmFluxo(shpPath.toFile(), consumidor);
        } finally {
            limparDiretorio(tempDir.toFile());
        }
    }

    private void lerViaDataStoreTemporario(InputStream is, String caminhoOriginal, Consumer<SimpleFeature> consumidor) throws IOException {
        String extensao = caminhoOriginal.contains(".") ? caminhoOriginal.substring(caminhoOriginal.lastIndexOf('.')) : ".tmp";
        Path tempFile = Files.createTempFile("georural-stream-", extensao);
        try {
            Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            lerShapefileLocalEmFluxo(tempFile.toFile(), consumidor);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void lerShapefileLocalEmFluxo(File file, Consumer<SimpleFeature> consumidor) throws IOException {
        Map<String, Object> map = new HashMap<>();
        map.put("url", file.toURI().toURL());

        DataStore dataStore = DataStoreFinder.getDataStore(map);
        if (dataStore == null) {
            throw new IOException("Não foi possível instanciar DataStore para o arquivo: " + file.getName());
        }

        try {
            String typeName = dataStore.getTypeNames()[0];
            FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(typeName);
            SimpleFeatureCollection collection = (SimpleFeatureCollection) source.getFeatures();

            try (SimpleFeatureIterator iterator = collection.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    consumidor.accept(feature);
                }
            }
        } finally {
            dataStore.dispose();
        }
    }

    private Path descompactarZipEEncontrarShp(InputStream zipInputStream, Path targetDir) throws IOException {
        Path shpPath = null;
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                File newFile = new File(targetDir.toFile(), entry.getName());
                if (!newFile.getCanonicalPath().startsWith(targetDir.toFile().getCanonicalPath())) {
                    throw new IOException("Entrada de arquivo Zip inválida: " + entry.getName());
                }
                newFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                if (entry.getName().toLowerCase().endsWith(".shp")) {
                    shpPath = newFile.toPath();
                }
                zis.closeEntry();
            }
        }
        return shpPath;
    }

    private void limparDiretorio(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    limparDiretorio(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}