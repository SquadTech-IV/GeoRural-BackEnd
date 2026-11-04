package br.com.fatec.georural.service.pipeline.leitor;

import org.locationtech.jts.geom.Geometry;

import java.util.Map;


public record FeicaoLida(
        long linha,
        Map<String, Object> atributos,
        Geometry geometria) {

    /** Primeiro atributo nao nulo entre os nomes dados, ignorando maiusculas. */
    public String atributoTexto(String... nomes) {
        if (atributos == null) {
            return null;
        }
        for (String nome : nomes) {
            for (Map.Entry<String, Object> e : atributos.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(nome) && e.getValue() != null) {
                    String v = String.valueOf(e.getValue()).trim();
                    if (!v.isEmpty()) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    public boolean temGeometria() {
        return geometria != null && !geometria.isEmpty();
    }
}
