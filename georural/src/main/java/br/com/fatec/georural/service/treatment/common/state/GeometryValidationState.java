package br.com.fatec.georural.service.treatment.common.state;

import java.util.ArrayList;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

/**
 * Estado que executa a validação e o reparo autônomo de geometrias (US#08 - T08 / Regra VAL-007).
 * - Geometrias válidas ou reparadas com sucesso via buffer(0) são aprovadas.
 * - Geometrias ausentes, vazias ou que permanecem inválidas após reparo são enviadas para a Quarentena.
 */
public class GeometryValidationState implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {

        List<SimpleFeature> validFeatures = new ArrayList<>();
        int entrada = context.getFeatures().size();

        for (SimpleFeature feature : context.getFeatures()) {

            Geometry geometry = (Geometry) feature.getDefaultGeometry();

            // 1. Geometria ausente
            if (geometry == null) {
                context.rejeitar(
                        "GEOMETRIA",
                        "the_geom",
                        "Geometria ausente ou nula no registro",
                        feature
                );
                continue;
            }

            // 2. Tentativa de reparo autônomo via buffer(0) para geometrias inválidas
            if (!geometry.isValid()) {
                try {
                    geometry = geometry.buffer(0);
                } catch (Exception e) {
                    context.rejeitar(
                            "GEOMETRIA",
                            "the_geom",
                            "Falha ao executar reparo autônomo na geometria: " + e.getMessage(),
                            feature
                    );
                    continue;
                }
            }

            // 3. Geometria vazia ou esvaziada após reparo
            if (geometry == null || geometry.isEmpty()) {
                context.rejeitar(
                        "GEOMETRIA",
                        "the_geom",
                        "Geometria vazia ou resultante vazia após reparo autônomo",
                        feature
                );
                continue;
            }

            // 4. Geometria permanece inválida após tentativa de reparo
            if (!geometry.isValid()) {
                context.rejeitar(
                        "GEOMETRIA",
                        "the_geom",
                        "Geometria inválida e não passível de reparo autônomo",
                        feature
                );
                continue;
            }

            feature.setDefaultGeometry(geometry);
            validFeatures.add(feature);
        }

        context.setFeatures(validFeatures);

        context.registrarTransformacao(
                "VALIDACAO_GEOMETRIA",
                entrada,
                validFeatures.size(),
                "rejeitadas=" + (entrada - validFeatures.size())
        );
    }
}