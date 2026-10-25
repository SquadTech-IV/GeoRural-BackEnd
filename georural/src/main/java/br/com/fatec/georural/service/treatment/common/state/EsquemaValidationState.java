package br.com.fatec.georural.service.treatment.common.state;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.entity.TipoConjunto;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.core.ValidadorEsquemaCamposService;
import org.geotools.api.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.List;

public class EsquemaValidationState implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {
        ValidadorEsquemaCamposService validador = SpringContextHolder.getBean(ValidadorEsquemaCamposService.class);
        
        TipoConjunto tipoConjunto = (context.getArquivoBruto() != null && context.getArquivoBruto().getConjunto() != null)
                ? context.getArquivoBruto().getConjunto().getTipo()
                : null;

        List<SimpleFeature> validFeatures = new ArrayList<>();
        int entrada = context.getFeatures().size();

        for (SimpleFeature feature : context.getFeatures()) {
            boolean aprovado = validador.validarRegistro(tipoConjunto, feature, context);
            if (aprovado) {
                validFeatures.add(feature);
            }
        }

        context.setFeatures(validFeatures);

        context.registrarTransformacao(
                "VALIDACAO_ESQUEMA",
                entrada,
                validFeatures.size(),
                "rejeitadas=" + (entrada - validFeatures.size())
        );
    }
}