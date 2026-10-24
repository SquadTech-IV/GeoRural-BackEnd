package br.com.fatec.georural.service.treatment.property_area.state;

import java.util.HashMap;
import java.util.Map;

import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.dto.request.regra.ProcessedProperty;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

public class ClipState implements TreatmentState {

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea =
                (PropertyAreaTreatment) baseContext;

        Map<String, ProcessedProperty> resultado =
                new HashMap<>();

        for (ProcessedProperty property :
                propertyArea.getProcessedProperties().values()) {

            Geometry geometry =
                    property.getGeometry4674();

            if (geometry == null) {
                continue;
            }

            if (!geometry.isValid()) {
                geometry = geometry.buffer(0);
            }

            if (geometry.isEmpty()) {
                continue;
            }

            property.setGeometry4674(
                    geometry);

            resultado.put(
                    property.getRecibo(),
                    property);
        }

        propertyArea.setProcessedProperties(
                resultado);

        propertyArea.setState(
                new DissolveState());
    }
}