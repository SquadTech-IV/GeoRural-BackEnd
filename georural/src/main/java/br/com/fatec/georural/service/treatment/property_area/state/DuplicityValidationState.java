package br.com.fatec.georural.service.treatment.property_area.state;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

public class DuplicityValidationState
        implements TreatmentState {

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea = (PropertyAreaTreatment) baseContext;

        Set<String> chavesVistas = new HashSet<>();

        // Lista (e não Set) para preservar a ordem original das feições
        List<SimpleFeature> featuresValidas = new ArrayList<>();

        int entrada = propertyArea.getFeatures().size();

        for (SimpleFeature feature : propertyArea.getFeatures()) {

            String recibo = (String) feature.getAttribute("recibo");

            String tema = (String) feature.getAttribute("tema");

            String chave = recibo + "|" + tema;

            if (!chavesVistas.add(chave)) {
                propertyArea.rejeitar("DUPLICIDADE", "recibo,tema",
                        "Registro duplicado: " + chave, feature);
                continue;
            }

            featuresValidas.add(feature);
        }

        propertyArea.setFeatures(featuresValidas);

        propertyArea.registrarTransformacao(
                "VALIDACAO_DUPLICIDADE",
                entrada,
                featuresValidas.size(),
                "duplicadas=" + (entrada - featuresValidas.size()));

        propertyArea.setState(new StandardizationState());
    }
}
