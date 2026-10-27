package br.com.fatec.georural.service.treatment.embargo.state;

import java.util.ArrayList;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

public class FieldsValidationState
        implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {

        List<SimpleFeature> validatedFeatures = new ArrayList<>();

        int entrada = context.getFeatures().size();

        for (SimpleFeature feature : context.getFeatures()) {

            Object numTad = resolverNumTad(feature);

            if (numTad == null || numTad.toString().isBlank()) {
                context.rejeitar("OBRIGATORIO", "num_tad",
                        "Número do TAD ausente", feature);
                continue;
            }

            Object geometry = feature.getDefaultGeometry();

            if (geometry == null) {
                context.rejeitar("GEOMETRIA", "the_geom",
                        "Geometria ausente", feature);
                continue;
            }

            validatedFeatures.add(feature);
        }

        context.setFeatures(validatedFeatures);

        context.registrarTransformacao(
                "VALIDACAO_CAMPOS",
                entrada,
                validatedFeatures.size(),
                "rejeitadas=" + (entrada - validatedFeatures.size()));

        context.setState(new DuplicityValidationState());
    }

    /**
     * Resolve o num_tad independentemente da caixa do campo. Shapefile e GeoJSON do IBAMA
     * ora vêm com "num_tad", ora com "NUM_TAD": buscar só minúsculo rejeitava o lote inteiro
     * ("Número do TAD ausente"). getAttribute(nome) devolve null quando o campo não existe
     * (não lança), então a varredura por caixa é segura.
     */
    private Object resolverNumTad(SimpleFeature feature) {
        Object v = feature.getAttribute("num_tad");
        if (v == null) {
            v = feature.getAttribute("NUM_TAD");
        }
        if (v == null) {
            for (var d : feature.getFeatureType().getAttributeDescriptors()) {
                if (d.getLocalName().equalsIgnoreCase("num_tad")) {
                    v = feature.getAttribute(d.getLocalName());
                    break;
                }
            }
        }
        return v;
    }
}
