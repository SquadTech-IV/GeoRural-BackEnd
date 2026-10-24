package br.com.fatec.georural.service.treatment.property_area.state;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.dto.request.regra.ProcessedProperty;
import br.com.fatec.georural.service.treatment.audit.TipoTransformacao;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

public class AreaCalculationState implements TreatmentState {

    /**
     * Albers de ÁREAS EQUIVALENTES para o Paraná (elipsoide GRS80, o do SIRGAS 2000).
     * Validada em Python contra os 399 municípios: maior desvio de 0,0058% em relação
     * ao AREA_KM2 do IBGE e de 0,0004% em relação à área geodésica.
     *
     * Substitui o EPSG:5880 (Policônica), que NÃO é equivalente: no mesmo teste
     * errou até +0,40% (litoral) e +0,10% no total do estado.
     *
     * Package-private para o teste AreaCrsValidationTest usar a mesma definição.
     */
    static final String AREA_CRS_WKT =
            "PROJCS[\"SIRGAS 2000 / Albers Parana\","
            + "GEOGCS[\"SIRGAS 2000\","
            + "DATUM[\"Sistema_de_Referencia_Geocentrico_para_las_AmericaS_2000\","
            + "SPHEROID[\"GRS 1980\",6378137,298.257222101]],"
            + "PRIMEM[\"Greenwich\",0],UNIT[\"degree\",0.0174532925199433]],"
            + "PROJECTION[\"Albers_Conic_Equal_Area\"],"
            + "PARAMETER[\"standard_parallel_1\",-23],"
            + "PARAMETER[\"standard_parallel_2\",-26],"
            + "PARAMETER[\"latitude_of_center\",-24.5],"
            + "PARAMETER[\"longitude_of_center\",-51.3],"
            + "PARAMETER[\"false_easting\",0],"
            + "PARAMETER[\"false_northing\",0],"
            + "UNIT[\"metre\",1]]";

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea = (PropertyAreaTreatment) baseContext;

        Map<String, Geometry> dissolvidas = propertyArea.getDissolvedGeometries();

        if (dissolvidas == null) {
            dissolvidas = new TreeMap<>();
        }

        Map<String, ProcessedProperty> properties = new TreeMap<>();

        Map<String, String> municipios = propertyArea.getMunicipiosPorRecibo();

        try {

            CoordinateReferenceSystem sourceCRS = CRS.decode("EPSG:4674", true);

            CoordinateReferenceSystem areaCRS = CRS.parseWKT(AREA_CRS_WKT);

            MathTransform transform = CRS.findMathTransform(sourceCRS, areaCRS, true);

            for (Map.Entry<String, Geometry> item : dissolvidas.entrySet()) {

                String recibo = item.getKey();

                Geometry geometry4674 = item.getValue();

                // defesa extra: garante o SRID exigido pelo índice espacial
                geometry4674.setSRID(4674);

                // A geometria usada só para medir; o que vai para o banco continua em 4674
                Geometry areaGeom = JTS.transform(geometry4674, transform);

                BigDecimal areaHa = BigDecimal.valueOf(areaGeom.getArea() / 10000d);

                ProcessedProperty pp = new ProcessedProperty();
                pp.setRecibo(recibo);
                pp.setMunicipio(municipios != null ? municipios.get(recibo) : null);
                pp.setEstado("Paraná");
                pp.setTema("Área do Imóvel");
                pp.setGeometry4674(geometry4674);
                pp.setAreaHa(areaHa);

                properties.put(recibo, pp);
            }

        } catch (Exception e) {

            throw new RuntimeException("Erro calculando áreas", e);
        }

        propertyArea.setProcessedProperties(properties);

        propertyArea.registrarTransformacao(
                TipoTransformacao.CALCULO_AREA,
                dissolvidas.size(),
                properties.size(),
                "CRS de área=Albers Paraná (equivalente)");

        propertyArea.setState(new PropertyAreaSavingState());
    }
}