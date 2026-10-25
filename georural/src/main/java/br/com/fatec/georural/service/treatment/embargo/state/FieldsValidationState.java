package br.com.fatec.georural.service.treatment.embargo.state;

import java.util.ArrayList;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

public class FieldsValidationState
        implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {

        List<SimpleFeature> validatedFeatures = new ArrayList<>();

        int entrada = context.getFeatures().size();

        for (SimpleFeature feature : context.getFeatures()) {

            Object numTad = feature.getAttribute("num_tad");

            if (numTad == null || numTad.toString().isBlank()) {
                context.rejeitar("OBRIGATORIO", "num_tad",
                        "Número do TAD ausente", feature);
                continue;
            }

            Object geometry = feature.getDefaultGeometry();

            if (geometry == null) {
                context.rejeitar("GEOMETRIA", "the_geom",
                        "Geometria ausente", feature);
                continue;
            }

            validatedFeatures.add(feature);
        }

        context.setFeatures(validatedFeatures);

        context.registrarTransformacao(
                "VALIDACAO_CAMPOS",
                entrada,
                validatedFeatures.size(),
                "rejeitadas=" + (entrada - validatedFeatures.size()));

        context.setState(new DuplicityValidationState());
    }
}
