package br.com.fatec.georural.service.pipeline.perfil;

import org.locationtech.jts.geom.Geometry;

// geometria que a camada aceita
public enum TipoGeometria {

    QUALQUER,
    PONTO,
    LINHA,
    POLIGONO;

    public boolean aceita(Geometry geometria) {
        if (geometria == null) {
            return false;
        }
        String tipo = geometria.getGeometryType();
        return switch (this) {
            case QUALQUER -> true;
            case PONTO -> tipo.equals("Point") || tipo.equals("MultiPoint");
            case LINHA -> tipo.equals("LineString") || tipo.equals("MultiLineString");
            case POLIGONO -> tipo.equals("Polygon") || tipo.equals("MultiPolygon");
        };
    }

    public String descricao() {
        return switch (this) {
            case QUALQUER -> "qualquer geometria";
            case PONTO -> "ponto";
            case LINHA -> "linha";
            case POLIGONO -> "poligono";
        };
    }
}
