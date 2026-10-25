package br.com.fatec.georural.service.treatment.common.state;

import java.util.ArrayList;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.service.treatment.audit.TransformacaoService;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

public class GeometryValidationState
        implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {

        List<SimpleFeature> validFeatures =
                new ArrayList<>();

        int entrada =
                context.getFeatures().size();

        for (SimpleFeature feature :
                context.getFeatures()) {

            Geometry geometry =
                    (Geometry) feature
                            .getDefaultGeometry();

            if (geometry == null) {
                continue;
            }

            if (!geometry.isValid()) {
                geometry =
                        geometry.buffer(0);
            }

            if (geometry.isEmpty()) {
                continue;
            }

            if (!geometry.isValid()) {
                continue;
            }

            feature.setDefaultGeometry(
                    geometry);

            validFeatures.add(
                    feature);
        }

        context.setFeatures(
                validFeatures);

        SpringContextHolder
                .getBean(
                        TransformacaoService.class)
                .registrar(
                        context.getExecucaoPipeline(),
                        1,
                        "VALIDACAO",
                        "Entrada="
                                + entrada
                                + ", saída="
                                + validFeatures.size());
    }
}