package br.com.fatec.georural.service.treatment.common.state;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.core.ValidadorAreaCoordenadaService;
import org.geotools.api.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado do pipeline que executa as validações de Áreas (VAL-005) e Coordenadas (VAL-006) (US#08 - T07).
 */
public class AreaCoordinateValidationState implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {
        ValidadorAreaCoordenadaService validador = SpringContextHolder.getBean(ValidadorAreaCoordenadaService.class);

        List<SimpleFeature> validFeatures = new ArrayList<>();
        int entrada = context.getFeatures().size();

        for (SimpleFeature feature : context.getFeatures()) {
            boolean aprovado = validador.validarRegistro(feature, context);
            if (aprovado) {
                validFeatures.add(feature);
            }
        }

        context.setFeatures(validFeatures);

        context.registrarTransformacao(
                "VALIDACAO_AREAS_COORDENADAS",
                entrada,
                validFeatures.size(),
                "rejeitadas=" + (entrada - validFeatures.size())
        );
    }
}