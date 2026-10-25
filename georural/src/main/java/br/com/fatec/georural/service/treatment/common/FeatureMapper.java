package br.com.fatec.georural.service.treatment.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.geotools.api.feature.Property;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;

/**
 * Converte uma feição em um mapa simples (atributos + geometria em WKT),
 * serializável em JSON. Usado pela QUARENTENA e pela zona tratada.
 */
public final class FeatureMapper {

    private FeatureMapper() {
    }

    public static Map<String, Object> paraMapa(SimpleFeature feature) {

        Map<String, Object> dados = new LinkedHashMap<>();

        for (Property p : feature.getProperties()) {

            String nome = p.getName().getLocalPart();
            Object valor = p.getValue();

            if (valor instanceof Geometry g) {
                dados.put(nome, g.toText());
            } else if (valor == null
                    || valor instanceof Number
                    || valor instanceof Boolean
                    || valor instanceof String) {
                dados.put(nome, valor);
            } else {
                dados.put(nome, String.valueOf(valor));
            }
        }

        return dados;
    }
}
