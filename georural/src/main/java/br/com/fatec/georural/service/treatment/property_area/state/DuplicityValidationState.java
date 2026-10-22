package br.com.fatec.georural.service.treatment.property_area.state;

import java.util.HashSet;
import java.util.Set;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.common.state.ReprojectionState;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

public class DuplicityValidationState
        implements TreatmentState {

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea =
                (PropertyAreaTreatment) baseContext;

        Set<String> chavesVistas =
                new HashSet<>();

        for (SimpleFeature feature :
                propertyArea.getFeatures()) {

            String recibo =
                    (String) feature.getAttribute("recibo");

            String tema =
                    (String) feature.getAttribute("tema");

            String chave =
                    recibo + "|" + tema;

            if (!chavesVistas.add(chave)) {

                throw new IllegalStateException(
                        "Registro duplicado: recibo="
                                + recibo
                                + ", tema="
                                + tema
                                + " (registro "
                                + feature.getID()
                                + ")");
            }
        }

        propertyArea.setState(
                new ReprojectionState());
    }
}