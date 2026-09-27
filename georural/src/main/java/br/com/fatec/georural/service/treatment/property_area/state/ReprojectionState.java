package br.com.fatec.georural.service.treatment.property_area.state;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateFilter;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.property_area.PropertyArea;

public class ReprojectionState implements PropertyAreaState {

    private static final String TARGET_CRS_CODE = "EPSG:4674";
    private static final int SRID_SIRGAS_2000 = 4674;


    private static final double LONG_MIN = -56.0;
    private static final double LONG_MAX = -47.0;
    private static final double LAT_MIN = -28.0;
    private static final double LAT_MAX = -21.0;

    @Override
    public void execute(PropertyArea propertyArea) {

        try {
            // Forca a interpretacao de coordenadas na ordem (Longitude, Latitude) / (X, Y)
            CoordinateReferenceSystem targetCRS = CRS.decode(TARGET_CRS_CODE, true);

            MathTransform transform = null;
            CoordinateReferenceSystem sourceCRS = null;

            for (SimpleFeature feature : propertyArea.getFeatures()) {

                SimpleFeatureType featureType = feature.getFeatureType();
                CoordinateReferenceSystem featureCRS = featureType.getCoordinateReferenceSystem();

                if (transform == null || !featureCRS.equals(sourceCRS)) {
                    sourceCRS = featureCRS;
                    transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
                }

                Geometry geometry = (Geometry) feature.getDefaultGeometry();

                Geometry reprojetada = JTS.transform(geometry, transform);

                reprojetada = corrigirOrdemDosEixosSeNecessario(reprojetada, feature.getID());

                // Define explicitamente o SRID na geometria JTS
                reprojetada.setSRID(SRID_SIRGAS_2000);

                feature.setDefaultGeometry(reprojetada);
            }

        } catch (FactoryException e) {
            throw new IllegalStateException(
                    "Erro ao resolver sistema de referência: " + e.getMessage(), e);

        } catch (TransformException e) {
            throw new IllegalStateException(
                    "Erro ao reprojetar geometria: " + e.getMessage(), e);
        }

        propertyArea.setState(new StandardizationState());
        propertyArea.execute();
    }


    private Geometry corrigirOrdemDosEixosSeNecessario(Geometry geometria, String featureId) {

        Coordinate c = geometria.getCoordinate();
        if (c == null) {
            return geometria;
        }

        boolean ordemCorreta = dentroDaFaixa(c.getX(), LONG_MIN, LONG_MAX)
                && dentroDaFaixa(c.getY(), LAT_MIN, LAT_MAX);

        boolean ordemInvertida = dentroDaFaixa(c.getX(), LAT_MIN, LAT_MAX)
                && dentroDaFaixa(c.getY(), LONG_MIN, LONG_MAX);

        if (ordemCorreta) {
            return geometria;
        }

        if (ordemInvertida) {
            return inverterEixos(geometria);
        }

        // Nao esta dentro de nenhuma das duas faixas esperadas: nao da pra decidir com
        // seguranca. Melhor falhar alto e mandar para quarentena do que gravar uma
        // geometria fora do Parana silenciosamente.
        throw new IllegalStateException(
                "Coordenadas fora dos limites esperados para o Parana (registro " + featureId
                        + "): x=" + c.getX() + ", y=" + c.getY());
    }

    private boolean dentroDaFaixa(double valor, double min, double max) {
        return valor >= min && valor <= max;
    }

    /**
     * Utilitario para inverter as coordenadas de (Lat, Lon) para (Lon, Lat)
     */
    private Geometry inverterEixos(Geometry geom) {

        Geometry geomClonada = (Geometry) geom.clone();

        geomClonada.apply(
                (CoordinateFilter) coord -> {
                    double x = coord.getX();
                    double y = coord.getY();

                    coord.setX(y);
                    coord.setY(x);
                });

        geomClonada.geometryChanged();

        return geomClonada;
    }
}