package br.com.fatec.georural.service.treatment.embargo.state;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class FieldsValidationState implements EmbargoState {

    @Override
    public void execute(Embargo context) {
        System.out.println("[3/8] Executando Validação de Campos...");
        SimpleFeatureCollection inputCollection = context.getCurrentFeatures();
        DefaultFeatureCollection validatedFieldsCollection = new DefaultFeatureCollection();

        try (SimpleFeatureIterator iterator = inputCollection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();

                Object numTad = feature.getAttribute("num_tad");
                Object geometry = feature.getDefaultGeometry();

                if (numTad != null && geometry != null) {
                    validatedFieldsCollection.add(feature);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro na validação de campos: " + e.getMessage(), e);
        }

        context.setCurrentFeatures(validatedFieldsCollection);
        context.setState(new DuplicityValidationState());
    }
}