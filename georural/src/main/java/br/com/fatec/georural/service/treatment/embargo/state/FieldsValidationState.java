<<<<<<< HEAD
package br.com.fatec.georural.service.treatment.embargo.state;

import java.util.ArrayList;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

public class FieldsValidationState implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {

        List<SimpleFeature> validatedFeatures = new ArrayList<>();

        for (SimpleFeature feature : context.getFeatures()) {

            Object numTad = feature.getAttribute("num_tad");
            Object geometry = feature.getDefaultGeometry();

            if (numTad != null && geometry != null) {
                validatedFeatures.add(feature);
            }
        }

        context.setFeatures(validatedFeatures);
        context.setState(new DuplicityValidationState());
    }
=======
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
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
}