package br.com.fatec.georural.services.treatment.property_area.state;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;

import br.com.fatec.georural.services.treatment.property_area.PropertyArea;

public class FieldsValidationState implements PropertyAreaState {

    @Override
    public void execute(PropertyArea propertyArea) {


        for (SimpleFeature feature : propertyArea.getFeatures()) {
            Object the_geom = feature.getAttribute("the_geom");
            if (the_geom == null) {
                throw new IllegalStateException("Campo obrigatório ausente: the_geom");
            }
        }

        propertyArea.setState(new DuplicityValidationState());
        propertyArea.execute();
    }
}
