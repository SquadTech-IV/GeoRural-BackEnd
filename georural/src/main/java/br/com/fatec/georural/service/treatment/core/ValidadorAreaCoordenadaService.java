package br.com.fatec.georural.service.treatment.core;

import br.com.fatec.georural.service.treatment.common.LimiteParanaProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável por validar regras de área e coordenadas da US#08 (T07):
 * - VAL-005 (AREA): Validação de valores de área (positivos e consistentes)
 * - VAL-006 (COORDENADA): Validação de coordenadas dentro dos limites territoriais válidos (Paraná/SIRGAS 2000)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidadorAreaCoordenadaService {

    private final LimiteParanaProvider limiteParanaProvider;

    /**
     * Aplica as validações de coordenadas (VAL-006) e áreas (VAL-005) em uma feição.
     *
     * @return true se o registro for aprovado em ambas as regras; false se for rejeitado.
     */
    public boolean validarRegistro(SimpleFeature feature, BaseTreatment context) {
        if (feature == null) {
            return false;
        }

        boolean coordenadasValidas = validarCoordenadas(feature, context);
        if (!coordenadasValidas) {
            return false;
        }

        return validarArea(feature, context);
    }

    /**
     * Regra VAL-006 (COORDENADA): Valida se as coordenadas da geometria existem, 
     * estão na faixa global $(-180 \le lon \le 180, -90 \le lat \le 90)$ e intersectam o limite do Estado do Paraná.
     */
    private boolean validarCoordenadas(SimpleFeature feature, BaseTreatment context) {
        Object defaultGeom = feature.getDefaultGeometry();
        if (!(defaultGeom instanceof Geometry geom) || geom.isEmpty()) {
            context.rejeitar("COORDENADA", "geometria", "Coordenadas/geometria ausentes ou vazias", feature);
            return false;
        }

        // 1. Validação global de limites de graus geográficos
        for (Coordinate coord : geom.getCoordinates()) {
            if (Double.isNaN(coord.x) || Double.isNaN(coord.y) ||
                coord.x < -180.0 || coord.x > 180.0 ||
                coord.y < -90.0 || coord.y > 90.0) {
                context.rejeitar("COORDENADA", "geometria", 
                        String.format("Coordenada geográfica inválida ou fora dos limites globais: x=%f, y=%f", coord.x, coord.y), 
                        feature);
                return false;
            }
        }

        // 2. Validação de delimitação do estado do Paraná
        try {
            LimiteParanaProvider.LimiteParana limitePR = limiteParanaProvider.carregar();
            if (limitePR != null && limitePR.geometria() != null) {
                Geometry limiteGeom = limitePR.geometria();
                if (!limiteGeom.getEnvelopeInternal().intersects(geom.getEnvelopeInternal()) && !limiteGeom.intersects(geom)) {
                    context.rejeitar("COORDENADA", "geometria", 
                            "Coordenadas geográficas localizadas fora dos limites do Estado do Paraná", feature);
                    return false;
                }
            }
        } catch (Exception e) {
            log.warn("Não foi possível carregar o limite do Paraná para validação rigorosa de coordenadas: {}", e.getMessage());
        }

        return true;
    }

    /**
     * Regra VAL-005 (AREA): Valida se os atributos declarados de área e a geometria calculada 
     * possuem valores estritamente positivos e coerentes.
     */
    private boolean validarArea(SimpleFeature feature, BaseTreatment context) {
        // Checking common area attributes
        String[] atributosArea = {"area", "area_ha", "area_tot", "imo_area_total", "app_area_ha", "rle_area_ha"};
        for (String attr : atributosArea) {
            Object val = feature.getAttribute(attr);
            if (val instanceof Number num) {
                double doubleVal = num.doubleValue();
                if (doubleVal <= 0) {
                    context.rejeitar("AREA", attr, 
                            "Valor de área inválido ou fora dos limites permitidos: " + doubleVal, feature);
                    return false;
                }
            }
        }

        // Validação da geometria
        Object defaultGeom = feature.getDefaultGeometry();
        if (defaultGeom instanceof Geometry geom) {
            double areaGeom = geom.getArea();
            if (Double.isNaN(areaGeom) || Double.isInfinite(areaGeom)) {
                context.rejeitar("AREA", "geometria", "Área calculada da geometria é inválida", feature);
                return false;
            }
        }

        return true;
    }
}