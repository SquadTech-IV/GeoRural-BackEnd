package br.com.fatec.georural.service.treatment.property_area.state;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

/**
 * Validação de campos do imóvel. Cada reprovação vai para a QUARENTENA com a
 * coluna e o motivo (US09.02). Os 1º argumentos de rejeitar() devem bater com
 * o campo "tipo" das linhas de REGRA_QUALIDADE.
 */
public class FieldsValidationState
        implements TreatmentState {

    private static final Pattern RECIBO_PATTERN = Pattern.compile("^[A-Z]{2}-\\d{7}-[0-9A-F]{32}$");

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea = (PropertyAreaTreatment) baseContext;

        List<SimpleFeature> validFeatures = new ArrayList<>();

        int entrada = propertyArea.getFeatures().size();

        for (SimpleFeature feature : propertyArea.getFeatures()) {

            Object recibo = feature.getAttribute("recibo");

            if (recibo == null || recibo.toString().isBlank()) {
                propertyArea.rejeitar("OBRIGATORIO", "recibo",
                        "Recibo ausente", feature);
                continue;
            }

            if (!RECIBO_PATTERN.matcher(recibo.toString()).matches()) {
                propertyArea.rejeitar("FORMATO", "recibo",
                        "Recibo fora do padrão: " + recibo, feature);
                continue;
            }

            Object tema = feature.getAttribute("tema");

            if (tema == null || tema.toString().isBlank()) {
                propertyArea.rejeitar("OBRIGATORIO", "tema",
                        "Tema ausente", feature);
                continue;
            }

            Object area = feature.getAttribute("area");

            if (area == null) {
                propertyArea.rejeitar("OBRIGATORIO", "area",
                        "Área ausente", feature);
                continue;
            }

            if (((Number) area).doubleValue() <= 0) {
                propertyArea.rejeitar("INTERVALO", "area",
                        "Área menor ou igual a zero: " + area, feature);
                continue;
            }

            Object modFiscais = feature.getAttribute("modfiscais");

            if (modFiscais == null) {
                propertyArea.rejeitar("OBRIGATORIO", "modfiscais",
                        "Módulos fiscais ausente", feature);
                continue;
            }

            if (((Number) modFiscais).doubleValue() < 0) {
                propertyArea.rejeitar("INTERVALO", "modfiscais",
                        "Módulos fiscais negativo: " + modFiscais, feature);
                continue;
            }

            Object municipio = feature.getAttribute("municipio");

            if (municipio == null || municipio.toString().isBlank()) {
                propertyArea.rejeitar("OBRIGATORIO", "municipio",
                        "Município ausente", feature);
                continue;
            }

            Object estado = feature.getAttribute("estado");

            if (estado == null || estado.toString().isBlank()) {
                propertyArea.rejeitar("OBRIGATORIO", "estado",
                        "Estado ausente", feature);
                continue;
            }

            String estadoNormalizado = Normalizer.normalize(
                    estado.toString().trim(),
                    Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toUpperCase();

            if (!"PARANA".equals(estadoNormalizado)
                    && !"PR".equals(estadoNormalizado)) {
                propertyArea.rejeitar("DOMINIO", "estado",
                        "Estado diferente de Paraná: " + estado, feature);
                continue;
            }

            validFeatures.add(feature);
        }

        propertyArea.setFeatures(validFeatures);

        propertyArea.registrarTransformacao(
                "VALIDACAO_CAMPOS",
                entrada,
                validFeatures.size(),
                "rejeitadas=" + (entrada - validFeatures.size()));

        propertyArea.setState(new DuplicityValidationState());
    }
}
