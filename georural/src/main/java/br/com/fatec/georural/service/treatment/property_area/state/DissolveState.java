package br.com.fatec.georural.service.treatment.property_area.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

public class DissolveState implements TreatmentState {

    private static final String TEMA_AREA_BRUTA = "Área do Imóvel";

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea = (PropertyAreaTreatment) baseContext;

        // TreeMap: ordem estável por recibo (saída determinística, US09.06)
        Map<String, List<Geometry>> agrupadas = new TreeMap<>();

        Map<String, String> municipios = new TreeMap<>();

        int entrada = 0;

        for (SimpleFeature feature : propertyArea.getFeatures()) {

            String tema = (String) feature.getAttribute("tema");

            if (!TEMA_AREA_BRUTA.equalsIgnoreCase(tema)) {
                continue;
            }

            entrada++;

            Geometry geom = (Geometry) feature.getDefaultGeometry();

            if (geom == null || geom.isEmpty()) {
                propertyArea.rejeitar("GEOMETRIA", "the_geom",
                        "Geometria vazia na dissolução", feature);
                continue;
            }

            String recibo = ((String) feature.getAttribute("recibo"))
                    .trim()
                    .toUpperCase();

            agrupadas
                    .computeIfAbsent(recibo, k -> new ArrayList<>())
                    .add(geom);

            municipios.putIfAbsent(
                    recibo,
                    (String) feature.getAttribute("municipio"));
        }

        Map<String, Geometry> dissolvidas = new TreeMap<>();

        for (Map.Entry<String, List<Geometry>> item : agrupadas.entrySet()) {

            Geometry uniao = UnaryUnionOp.union(item.getValue());

            // o JTS devolve a união com SRID 0; o índice espacial exige 4674
            uniao.setSRID(SRID_SIRGAS_2000);

            dissolvidas.put(item.getKey(), uniao);
        }

        propertyArea.registrarTransformacao(
                "DISSOLVER",
                entrada,
                dissolvidas.size(),
                "recibos dissolvidos=" + dissolvidas.size());

        propertyArea.setDissolvedGeometries(dissolvidas);

        propertyArea.setMunicipiosPorRecibo(municipios);

        propertyArea.setState(new AreaCalculationState());
    }
}
