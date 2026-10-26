package br.com.fatec.georural.service.treatment.property_area.state;

import java.util.ArrayList;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygonal;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.geom.util.PolygonExtracter;

import br.com.fatec.georural.service.treatment.audit.TipoTransformacao;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

/**
 * Recorte dos imóveis pelo limite do Paraná (US09.01).
 *
 * Roda depois da reprojeção (feições em SIRGAS 2000 / 4674) e antes do dissolve.
 * - totalmente dentro do PR: mantido como está;
 * - parcialmente dentro: geometria recortada (só a parte dentro do PR);
 * - totalmente fora: vai para a QUARENTENA.
 *
 * ATENÇÃO: imóveis que cruzam a divisa perdem a parte fora do PR, então a área
 * calculada depois é a da parte dentro do Paraná.
 */
public class ClipState implements TreatmentState {

    private static final String SRID_CODE = "EPSG:4674";

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea = (PropertyAreaTreatment) baseContext;

        Geometry limite = propertyArea.getAreaDeCorte();

        if (limite == null) {
            throw new IllegalStateException(
                    "Limite do Paraná não carregado para o recorte do imóvel");
        }

        limite = paraSirgas2000(limite, propertyArea.getAreaDeCorteCRS());

        PreparedGeometry limitePreparado = PreparedGeometryFactory.prepare(limite);

        List<SimpleFeature> resultado = new ArrayList<>();

        int entrada = propertyArea.getFeatures().size();
        int dentro = 0;
        int recortadas = 0;
        int fora = 0;

        for (SimpleFeature feature : propertyArea.getFeatures()) {

            Geometry geom = (Geometry) feature.getDefaultGeometry();

            if (geom == null || geom.isEmpty()) {
                propertyArea.rejeitar("GEOMETRIA", "the_geom",
                        "Geometria ausente ou vazia no recorte", feature);
                continue;
            }

            try {

                if (!geom.isValid()) {
                    geom = geom.buffer(0);
                }

                if (!limitePreparado.intersects(geom)) {
                    propertyArea.rejeitar("DOMINIO", "the_geom",
                            "Imóvel fora do limite do Paraná", feature);
                    fora++;
                    continue;
                }

                if (limitePreparado.covers(geom)) {
                    geom.setSRID(4674);
                    feature.setDefaultGeometry(geom);
                    resultado.add(feature);
                    dentro++;
                    continue;
                }

                Geometry recortada = apenasPoligonos(limite.intersection(geom));

                if (recortada == null || recortada.isEmpty()) {
                    propertyArea.rejeitar("DOMINIO", "the_geom",
                            "Imóvel sem área dentro do limite do Paraná", feature);
                    fora++;
                    continue;
                }

                recortada.setSRID(4674);
                feature.setDefaultGeometry(recortada);
                resultado.add(feature);
                recortadas++;

            } catch (Exception e) {
                propertyArea.rejeitar("GEOMETRIA", "the_geom",
                        "Falha no recorte: " + e.getMessage(), feature);
            }
        }

        propertyArea.setFeatures(resultado);

        propertyArea.registrarTransformacao(
                TipoTransformacao.RECORTE,
                entrada,
                resultado.size(),
                "limite=PR, dentro=" + dentro
                        + ", recortadas=" + recortadas
                        + ", fora=" + fora);

        propertyArea.setState(new DissolveState());
    }

    private Geometry paraSirgas2000(Geometry geometria, CoordinateReferenceSystem crsOrigem) {

        try {
            CoordinateReferenceSystem alvo = CRS.decode(SRID_CODE, true);

            if (crsOrigem == null || CRS.equalsIgnoreMetadata(crsOrigem, alvo)) {
                return geometria;
            }

            MathTransform transform = CRS.findMathTransform(crsOrigem, alvo, true);

            return JTS.transform(geometria, transform);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Erro ao converter o limite do Paraná para EPSG:4674: " + e.getMessage(),
                    e);
        }
    }

    /** A interseção pode devolver linhas/pontos junto; fica só com os polígonos. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Geometry apenasPoligonos(Geometry g) {

        if (g == null || g.isEmpty()) {
            return null;
        }

        if (g instanceof Polygonal) {
            return g;
        }

        List poligonos = PolygonExtracter.getPolygons(g);

        if (poligonos.isEmpty()) {
            return null;
        }

        return g.getFactory().buildGeometry(poligonos);
    }
}