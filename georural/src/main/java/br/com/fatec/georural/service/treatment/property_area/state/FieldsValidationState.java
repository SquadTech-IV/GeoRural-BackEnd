package br.com.fatec.georural.service.treatment.property_area.state;

import java.text.Normalizer;
import java.util.regex.Pattern;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

public class FieldsValidationState implements TreatmentState {

    private static final Pattern RECIBO_PATTERN =
            Pattern.compile("^[A-Z]{2}-\\d{7}-[0-9A-F]{32}$");

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea =
                (PropertyAreaTreatment) baseContext;

        for (SimpleFeature feature :
                propertyArea.getFeatures()) {

            String id = feature.getID();

            Object recibo =
                    feature.getAttribute("recibo");

            if (recibo == null ||
                    recibo.toString().isBlank()) {

                throw new IllegalStateException(
                        "Campo obrigatório ausente: recibo (registro "
                                + id + ")");
            }

            if (!RECIBO_PATTERN.matcher(
                    recibo.toString()).matches()) {

                throw new IllegalStateException(
                        "Formato de recibo inválido: "
                                + recibo
                                + " (registro "
                                + id + ")");
            }

            Object tema =
                    feature.getAttribute("tema");

            if (tema == null ||
                    tema.toString().isBlank()) {

                throw new IllegalStateException(
                        "Campo obrigatório ausente: tema (registro "
                                + id + ")");
            }

            Object area =
                    feature.getAttribute("area");

            if (area == null) {

                throw new IllegalStateException(
                        "Campo obrigatório ausente: area (registro "
                                + id + ")");
            }

            if (((Number) area).doubleValue() <= 0) {

                throw new IllegalStateException(
                        "Área inválida (<= 0) no registro "
                                + id);
            }

            Object modFiscais =
                    feature.getAttribute("modfiscais");

            if (modFiscais == null) {

                throw new IllegalStateException(
                        "Campo obrigatório ausente: modfiscais (registro "
                                + id + ")");
            }

            if (((Number) modFiscais).doubleValue() < 0) {

                throw new IllegalStateException(
                        "Módulos fiscais inválido (negativo) no registro "
                                + id);
            }

            Object municipio =
                    feature.getAttribute("municipio");

            if (municipio == null ||
                    municipio.toString().isBlank()) {

                throw new IllegalStateException(
                        "Campo obrigatório ausente: municipio (registro "
                                + id + ")");
            }

            Object estado =
                    feature.getAttribute("estado");

            if (estado == null ||
                    estado.toString().isBlank()) {

                throw new IllegalStateException(
                        "Campo obrigatório ausente: estado (registro "
                                + id + ")");
            }

            String estadoNormalizado =
                    Normalizer.normalize(
                            estado.toString().trim(),
                            Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "")
                            .toUpperCase();

            if (!"PARANA".equals(estadoNormalizado)
                    && !"PR".equals(estadoNormalizado)) {

                throw new IllegalStateException(
                        "Registro fora do escopo (estado != Paraná): "
                                + estado
                                + " (registro "
                                + id + ")");
            }
        }

        propertyArea.setState(
                new DuplicityValidationState());
    }
}