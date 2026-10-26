package br.com.fatec.georural.service.treatment.common.state;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeometryValidationStateTest {

    @Mock
    private BaseTreatment context;

    private final GeometryValidationState state = new GeometryValidationState();
    private final GeometryFactory gf = new GeometryFactory();

    @Test
    @DisplayName("VAL-007: Deve reparar autônomamente geometria com laço/auto-intersecção via buffer(0)")
    void deveRepararGeometriaComLaco() {
        // Polígono em formato "8" (laço/auto-intersecção)
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(0, 2),
                new Coordinate(2, 0), new Coordinate(2, 2),
                new Coordinate(0, 0)
        };
        Polygon geomInvalida = gf.createPolygon(coords);
        assertFalse(geomInvalida.isValid());

        SimpleFeature feature = criarFeature(geomInvalida);
        List<SimpleFeature> features = new ArrayList<>(List.of(feature));
        when(context.getFeatures()).thenReturn(features);

        state.execute(context);

        verify(context, never()).rejeitar(anyString(), anyString(), anyString(), any());
        verify(context).setFeatures(argThat(list -> list.size() == 1));
    }

    @Test
    @DisplayName("VAL-007: Deve enviar para quarentena se a geometria for nula")
    void deveRejeitarGeometriaNula() {
        SimpleFeature feature = criarFeature(null);
        when(context.getFeatures()).thenReturn(new ArrayList<>(List.of(feature)));

        state.execute(context);

        verify(context).rejeitar(eq("GEOMETRIA"), eq("the_geom"), anyString(), eq(feature));
    }

    private SimpleFeature criarFeature(Polygon geom) {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("FeatureGeom");
        typeBuilder.add("the_geom", Polygon.class);

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(typeBuilder.buildFeatureType());
        builder.add(geom);
        return builder.buildFeature("1");
    }
}