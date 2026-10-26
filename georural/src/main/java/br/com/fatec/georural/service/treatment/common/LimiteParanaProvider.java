package br.com.fatec.georural.service.treatment.common;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import org.springframework.stereotype.Component;

/**
 * Fonte única do limite do Paraná usado no recorte. Os fluxos de embargo e de
 * imóvel passam a usar o MESMO limite (US09.01). O resultado é cacheado.
 */
@Component
public class LimiteParanaProvider {

    public static final String LIMITE_PARANA_PATH = "src/main/resources/shapefiles/limite_estadual_pr.shp";

    public record LimiteParana(
            Geometry geometria,
            CoordinateReferenceSystem crs) {
    }

    private volatile LimiteParana cache;

    public LimiteParana carregar() {

        LimiteParana atual = cache;

        if (atual != null) {
            return atual;
        }

        synchronized (this) {

            if (cache == null) {
                cache = lerDoArquivo();
            }

            return cache;
        }
    }

    private LimiteParana lerDoArquivo() {

        File arquivo = new File(LIMITE_PARANA_PATH);

        if (!arquivo.exists()) {
            throw new IllegalStateException(
                    "Shapefile de limite do Paraná não encontrado em: "
                            + LIMITE_PARANA_PATH);
        }

        FileDataStore store = null;

        try {
            store = FileDataStoreFinder.getDataStore(arquivo);

            if (store == null) {
                throw new IOException(
                        "Não foi possível abrir o shapefile: "
                                + arquivo.getAbsolutePath());
            }

            CoordinateReferenceSystem crs = store.getSchema()
                    .getCoordinateReferenceSystem();

            SimpleFeatureCollection collection = store
                    .getFeatureSource()
                    .getFeatures();

            List<Geometry> geometrias = new ArrayList<>();

            try (SimpleFeatureIterator it = collection.features()) {
                while (it.hasNext()) {
                    Geometry g = (Geometry) it.next().getDefaultGeometry();
                    if (g != null && !g.isEmpty()) {
                        geometrias.add(g);
                    }
                }
            }

            if (geometrias.isEmpty()) {
                throw new IllegalStateException(
                        "Shapefile de limite do Paraná não contém nenhuma geometria válida para o recorte.");
            }

            Geometry uniao = UnaryUnionOp.union(geometrias);

            return new LimiteParana(uniao, crs);

        } catch (IOException e) {
            throw new RuntimeException(
                    "Erro ao ler shapefile de limite do Paraná: " + e.getMessage(),
                    e);
        } finally {
            if (store != null) {
                store.dispose();
            }
        }
    }
}
