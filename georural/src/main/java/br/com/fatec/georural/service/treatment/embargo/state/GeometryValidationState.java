package br.com.fatec.georural.service.treatment.embargo.state;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

/**
 * 2º passo: valida geometria ANTES do Clip/Dissolve. Isso é essencial —
 * geom.intersection() e union() (nos próximos passos) exigem geometrias
 * topologicamente válidas; validar depois desses passos arrisca
 * TopologyException ou resultados incorretos silenciosos.
 */
public class GeometryValidationState implements EmbargoState {

    @Override
    public void execute(Embargo context) {
        System.out.println("[2/8] Executando Validação de Geometria...");
        SimpleFeatureCollection inputCollection = context.getCurrentFeatures();
        DefaultFeatureCollection validCollection = new DefaultFeatureCollection();

        try (SimpleFeatureIterator iterator = inputCollection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Geometry geom = (Geometry) feature.getDefaultGeometry();

                if (geom != null) {
                    if (!geom.isValid()) {
                        geom = geom.buffer(0);
                    }
                    if (!geom.isEmpty()) {
                        feature.setDefaultGeometry(geom);
                        validCollection.add(feature);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro na validação de geometria: " + e.getMessage(), e);
        }

        context.setCurrentFeatures(validCollection);
        context.setState(new FieldsValidationState());
    }
}