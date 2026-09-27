package br.com.fatec.georural.service.treatment.embargo.state;

import java.io.File;

import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class ReprojectionState implements EmbargoState {

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(Embargo context) {
        System.out.println("[1/8] Executando Reprojeção...");
        File file = context.getEmbargoFile();
        DefaultFeatureCollection reprojectedCollection = new DefaultFeatureCollection();

        try {
            FileDataStore store = FileDataStoreFinder.getDataStore(file);
            CoordinateReferenceSystem sourceCRS = store.getSchema().getCoordinateReferenceSystem();
            CoordinateReferenceSystem targetCRS = context.getTargetCRS();
            MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);

            SimpleFeatureCollection featureCollection = store.getFeatureSource().getFeatures();

            try (SimpleFeatureIterator iterator = featureCollection.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();
                    Geometry geom = (Geometry) feature.getDefaultGeometry();
                    if (geom != null) {
                        Geometry transformedGeom = JTS.transform(geom, transform);
                        transformedGeom.setSRID(SRID_SIRGAS_2000);
                        feature.setDefaultGeometry(transformedGeom);
                        reprojectedCollection.add(feature);
                    }
                }
            }
            store.dispose();

            // Reprojeta a geometria do Estado do Paraná caso esteja em outro sistema de coordenadas
            if (context.getAreaDeCorte() != null) {
                CoordinateReferenceSystem areaDeCorteCRS = context.getAreaDeCorteCRS();
                if (areaDeCorteCRS != null && !areaDeCorteCRS.equals(targetCRS)) {
                    MathTransform transformArea = CRS.findMathTransform(areaDeCorteCRS, targetCRS, true);
                    Geometry areaDeCorteReprojetada = JTS.transform(context.getAreaDeCorte(), transformArea);
                    areaDeCorteReprojetada.setSRID(SRID_SIRGAS_2000);
                    context.setAreaDeCorte(areaDeCorteReprojetada);
                } else {
                    context.getAreaDeCorte().setSRID(SRID_SIRGAS_2000);
                }
            }

            context.setCurrentFeatures(reprojectedCollection);
            context.setState(new GeometryValidationState());
        } catch (Exception e) {
            throw new RuntimeException("Erro ao re-projetar geometrias: " + e.getMessage(), e);
        }
    }
}