package br.com.fatec.georural.service.treatment.embargo.state;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class DissolveState implements EmbargoState {

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(Embargo context) {
        System.out.println("[6/8] Executando Dissolve...");
        SimpleFeatureCollection inputCollection = context.getCurrentFeatures();

        if (inputCollection == null || inputCollection.isEmpty()) {
            System.out.println("Coleção vazia no Dissolve. Avançando...");
            context.setState(new StandardizationState());
            return;
        }

        DefaultFeatureCollection outputCollection = new DefaultFeatureCollection();

        try (SimpleFeatureIterator iterator = inputCollection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Geometry geom = (Geometry) feature.getDefaultGeometry();

                if (geom != null && !geom.isEmpty()) {
                    if (!geom.isValid()) {
                        geom = geom.buffer(0);
                    }
                    geom.setSRID(SRID_SIRGAS_2000);
                    feature.setDefaultGeometry(geom);
                    outputCollection.add(feature);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro no Dissolve: " + e.getMessage());
        }

        System.out.println("Feições após Dissolve: " + outputCollection.size());
        context.setCurrentFeatures(outputCollection);
        context.setState(new StandardizationState());
    }
}