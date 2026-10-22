package br.com.fatec.georural.service.treatment.common.state;

import java.util.ArrayList;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

public class GeometryValidationState implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {

        System.out.println(
                "[GeometryValidationState] Validando geometrias..."
        );

        List<SimpleFeature> validFeatures =
                new ArrayList<>();

        try {

            for (SimpleFeature feature :
                    context.getFeatures()) {

                Geometry geometry =
                        (Geometry) feature.getDefaultGeometry();

                if (geometry == null) {
                    continue;
                }

                if (!geometry.isValid()) {
                    geometry = geometry.buffer(0);
                }

                if (geometry.isEmpty()) {
                    continue;
                }

                if (!geometry.isValid()) {
                    throw new IllegalStateException(
                            "Geometria inválida encontrada no registro: "
                                    + feature.getID());
                }

                feature.setDefaultGeometry(
                        geometry);

                validFeatures.add(feature);
            }

        } catch (Exception e) {

            throw new RuntimeException(
                    "Erro na validação de geometria: "
                            + e.getMessage(),
                    e);
        }

        context.setFeatures(validFeatures);
    }
}