package br.com.fatec.georural.service.treatment.embargo.state;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class ClipState implements EmbargoState {

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(Embargo context) {
        System.out.println("[5/8] Executando Recorte (Clip para Estado do Paraná)...");
        Geometry estadoParana = context.getAreaDeCorte();
        SimpleFeatureCollection inputCollection = context.getCurrentFeatures();
        DefaultFeatureCollection clippedCollection = new DefaultFeatureCollection();

        if (inputCollection == null || inputCollection.isEmpty()) {
            System.out.println("Aviso: Nenhuma feição recebida no ClipState.");
            context.setState(new DissolveState());
            return;
        }

        try (SimpleFeatureIterator iterator = inputCollection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Geometry geom = (Geometry) feature.getDefaultGeometry();

                if (geom != null) {
                    // Se não tiver área de corte ou se interceptar o estado do Paraná
                    if (estadoParana == null || geom.intersects(estadoParana)) {
                        Geometry clippedGeom = (estadoParana != null) ? geom.intersection(estadoParana) : geom;

                        if (clippedGeom != null && !clippedGeom.isEmpty()) {
                            if (!clippedGeom.isValid()) {
                                clippedGeom = clippedGeom.buffer(0);
                            }
                            clippedGeom.setSRID(SRID_SIRGAS_2000);
                            feature.setDefaultGeometry(clippedGeom);
                            clippedCollection.add(feature);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro no Clip: " + e.getMessage() + ". Prosseguindo com coleção original.");
            clippedCollection = (DefaultFeatureCollection) inputCollection;
        }

        System.out.println("Feições restantes após Clip: " + clippedCollection.size());
        context.setCurrentFeatures(clippedCollection);
        context.setState(new DissolveState());
    }
}