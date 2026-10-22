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
}