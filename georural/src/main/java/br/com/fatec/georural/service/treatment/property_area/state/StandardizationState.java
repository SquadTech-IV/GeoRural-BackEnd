package br.com.fatec.georural.service.treatment.property_area.state;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.property_area.PropertyArea;

public class StandardizationState implements PropertyAreaState {

    private static final String TEMA_AREA_BRUTA = "Área do Imóvel";
    private static final String TEMA_AREA_LIQUIDA = "Área Líquida do Imóvel";

    @Override
    public void execute(PropertyArea propertyArea) {

        for (SimpleFeature feature : propertyArea.getFeatures()) {

            String recibo = (String) feature.getAttribute("recibo");
            feature.setAttribute("recibo", recibo.trim().toUpperCase());

            String municipio = (String) feature.getAttribute("municipio");
            feature.setAttribute("municipio", municipio.trim());


            feature.setAttribute("estado", "Paraná");

            String tema = (String) feature.getAttribute("tema");
            feature.setAttribute("tema", normalizarTema(tema));
        }

        propertyArea.setState(new PropertyAreaSavingState());
        propertyArea.execute();
    }

    private String normalizarTema(String tema) {
        String temaSemEspacos = tema.trim();

        if (temaSemEspacos.equalsIgnoreCase("Área do Imovel")
                || temaSemEspacos.equalsIgnoreCase(TEMA_AREA_BRUTA)) {
            return TEMA_AREA_BRUTA;
        }

        if (temaSemEspacos.equalsIgnoreCase("Área Líquida do Imovel")
                || temaSemEspacos.equalsIgnoreCase(TEMA_AREA_LIQUIDA)) {
            return TEMA_AREA_LIQUIDA;
        }

        throw new IllegalStateException("Valor de tema não reconhecido: " + tema);
    }
}