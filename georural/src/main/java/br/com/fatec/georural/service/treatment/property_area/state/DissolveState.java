package br.com.fatec.georural.service.treatment.property_area.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;
import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.service.treatment.audit.TransformacaoService;

public class DissolveState implements TreatmentState {

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea =
                (PropertyAreaTreatment) baseContext;

        Map<String, List<Geometry>> agrupadas =
                new HashMap<>();

        for (SimpleFeature feature :
                propertyArea.getFeatures()) {

            String tema =
                    (String) feature.getAttribute("tema");

            if (!"Área do Imóvel"
                    .equalsIgnoreCase(tema)) {
                continue;
            }

            String recibo =
                    ((String) feature.getAttribute("recibo"))
                            .trim()
                            .toUpperCase();

            Geometry geom =
                    (Geometry) feature.getDefaultGeometry();

            agrupadas
                    .computeIfAbsent(
                            recibo,
                            k -> new ArrayList<>())
                    .add(geom);
        }

        Map<String, Geometry> dissolvidas =
                new HashMap<>();

        for (Map.Entry<String, List<Geometry>> item :
                agrupadas.entrySet()) {

            Geometry uniao =
                    UnaryUnionOp.union(
                            item.getValue());

            dissolvidas.put(
                    item.getKey(),
                    uniao);
        }

        SpringContextHolder
        .getBean(
                TransformacaoService.class)
        .registrar(
                propertyArea.getExecucaoPipeline(),
                4,
                "DISSOLVER",
                "Recibos dissolvidos="
                        + dissolvidas.size());

        propertyArea.setDissolvedGeometries(
                dissolvidas);

        propertyArea.setState(
                new AreaCalculationState());
    }
}