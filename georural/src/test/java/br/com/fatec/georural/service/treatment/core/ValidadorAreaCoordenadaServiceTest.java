package br.com.fatec.georural.service.treatment.core;

import br.com.fatec.georural.service.treatment.common.LimiteParanaProvider;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidadorAreaCoordenadaServiceTest {

    @Mock
    private LimiteParanaProvider limiteParanaProvider;

    @Mock
    private BaseTreatment context;

    private ValidadorAreaCoordenadaService service;
    private final GeometryFactory gf = new GeometryFactory();

    @BeforeEach
    void setUp() {
        service = new ValidadorAreaCoordenadaService(limiteParanaProvider);
    }

    @Test
    @DisplayName("VAL-005: Deve rejeitar área menor ou igual a zero")
    void deveRejeitarAreaInvalida() {
        Polygon poly = gf.createPolygon(new Coordinate[]{
                new Coordinate(-51.0, -25.0), new Coordinate(-51.0, -25.1),
                new Coordinate(-51.1, -25.1), new Coordinate(-51.0, -25.0)
        });

        SimpleFeature feature = criarFeature(poly, -10.0);
        boolean resultado = service.validarRegistro(feature, context);

        assertFalse(resultado);
        verify(context).rejeitar(eq("AREA"), eq("area_ha"), anyString(), eq(feature));
    }

    @Test
    @DisplayName("VAL-006: Deve rejeitar coordenadas geográficas fora da faixa global")
    void deveRejeitarCoordenadasForaRange() {
        Polygon polyInvalido = gf.createPolygon(new Coordinate[]{
                new Coordinate(-200.0, -25.0), new Coordinate(-200.0, -25.1),
                new Coordinate(-200.1, -25.1), new Coordinate(-200.0, -25.0)
        });

        SimpleFeature feature = criarFeature(polyInvalido, 100.0);
        boolean resultado = service.validarRegistro(feature, context);

        assertFalse(resultado);
        verify(context).rejeitar(eq("COORDENADA"), eq("geometria"), anyString(), eq(feature));
    }

    private SimpleFeature criarFeature(Polygon geom, double area) {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("FeatureGeom");
        typeBuilder.add("geometria", Polygon.class);
        typeBuilder.add("area_ha", Double.class);

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(typeBuilder.buildFeatureType());
        builder.add(geom);
        builder.add(area);
        return builder.buildFeature("1");
    }
}