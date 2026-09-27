package br.com.fatec.georural.service.treatment.property_area.state;

import java.util.HashSet;
import java.util.Set;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.property_area.PropertyArea;

public class DuplicityValidationState implements PropertyAreaState {

    @Override
    public void execute(PropertyArea propertyArea) {

        Set<String> chavesVistas = new HashSet<>();

        for (SimpleFeature feature : propertyArea.getFeatures()) {

            String recibo = (String) feature.getAttribute("recibo");
            String tema = (String) feature.getAttribute("tema");

            String chave = recibo + "|" + tema;

            if (!chavesVistas.add(chave)) {
                throw new IllegalStateException(
                        "Registro duplicado: recibo=" + recibo + ", tema=" + tema
                                + " (registro " + feature.getID() + ")");
            }
        }

        propertyArea.setState(new ReprojectionState());
        propertyArea.execute();
    }
}