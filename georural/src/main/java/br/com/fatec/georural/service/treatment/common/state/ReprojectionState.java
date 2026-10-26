package br.com.fatec.georural.service.treatment.common.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.service.treatment.audit.TransformacaoService;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

public class ReprojectionState implements TreatmentState {

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(BaseTreatment context) {

        try {

            CoordinateReferenceSystem targetCRS =
                    CRS.decode("EPSG:4674", true);

            Map<CoordinateReferenceSystem, MathTransform>
                    transformacoes =
                    new HashMap<>();

            List<SimpleFeature> reprojetadas =
                    new ArrayList<>(
                            context.getFeatures().size());

            int antes =
                    context.getFeatures().size();

            for (SimpleFeature feature :
                    context.getFeatures()) {

                Geometry geometry =
                        (Geometry) feature
                                .getDefaultGeometry();

                if (geometry == null) {
                    continue;
                }

                CoordinateReferenceSystem
                        sourceCRS =
                        feature.getFeatureType()
                                .getCoordinateReferenceSystem();

                Geometry resultado =
                        geometry;

                if (sourceCRS != null) {

                    MathTransform transform =
                            transformacoes
                                    .get(sourceCRS);

                    if (transform == null) {

                        transform =
                                CRS.findMathTransform(
                                        sourceCRS,
                                        targetCRS,
                                        true);

                        transformacoes.put(
                                sourceCRS,
                                transform);
                    }

                    resultado =
                            JTS.transform(
                                    geometry,
                                    transform);
                }

                resultado.setSRID(
                        SRID_SIRGAS_2000);

                feature.setDefaultGeometry(
                        resultado);

                reprojetadas.add(feature);
            }

            context.setFeatures(
                    reprojetadas);

            SpringContextHolder
                    .getBean(
                            TransformacaoService.class)
                    .registrar(
                            context.getExecucaoPipeline(),
                            2,
                            "REPROJECAO",
                            "Feições reprojetadas para EPSG:4674. Entrada="
                                    + antes
                                    + ", saída="
                                    + reprojetadas.size());

        } catch (Exception e) {

            throw new RuntimeException(
                    "Erro ao reprojetar geometrias",
                    e);
        }
    }
}