package br.com.fatec.georural.service.treatment.property_area.state;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

public class StandardizationState
        implements TreatmentState {

    private static final String TEMA_AREA_BRUTA =
            "Área do Imóvel";

    private static final String TEMA_AREA_LIQUIDA =
            "Área Líquida do Imóvel";

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea =
                (PropertyAreaTreatment) baseContext;

        for (SimpleFeature feature :
                propertyArea.getFeatures()) {

            String recibo =
                    ((String) feature.getAttribute("recibo"))
                            .trim()
                            .toUpperCase();

            feature.setAttribute(
                    "recibo",
                    recibo);

            String municipio =
                    (String) feature.getAttribute("municipio");

            feature.setAttribute(
                    "municipio",
                    municipio.trim());

            feature.setAttribute(
                    "estado",
                    "Paraná");

            String tema =
                    (String) feature.getAttribute("tema");

            feature.setAttribute(
                    "tema",
                    normalizarTema(tema));
        }

        propertyArea.setState(
                new PropertyAreaSavingState());
    }

    private String normalizarTema(
            String tema) {

        String temaSemEspacos =
                tema.trim();

        if (temaSemEspacos.equalsIgnoreCase("Área do Imovel")
                || temaSemEspacos.equalsIgnoreCase(TEMA_AREA_BRUTA)) {

            return TEMA_AREA_BRUTA;
        }

        if (temaSemEspacos.equalsIgnoreCase("Área Líquida do Imovel")
                || temaSemEspacos.equalsIgnoreCase(TEMA_AREA_LIQUIDA)) {

            return TEMA_AREA_LIQUIDA;
        }

        throw new IllegalStateException(
                "Valor de tema não reconhecido: "
                        + tema);
    }
}