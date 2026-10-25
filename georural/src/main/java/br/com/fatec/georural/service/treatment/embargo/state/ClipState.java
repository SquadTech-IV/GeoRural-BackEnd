package br.com.fatec.georural.service.treatment.embargo.state;

import java.util.ArrayList;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.embargo.EmbargoTreatment;

public class ClipState implements TreatmentState {

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(BaseTreatment baseContext) {

        EmbargoTreatment context = (EmbargoTreatment) baseContext;

        System.out.println("[Clip] Recortando pelo limite do Paraná...");

        Geometry estadoParana = context.getAreaDeCorte();

        // Sem limite não existe recorte correto. Falhar é melhor do que gravar o Brasil inteiro.
        if (estadoParana == null) {
            throw new IllegalStateException(
                    "Limite do Paraná não carregado: não é possível recortar os embargos.");
        }

        try {

            // O limite vem de outro shapefile; garante que está no mesmo CRS das feições
            CoordinateReferenceSystem alvo = CRS.decode("EPSG:4674", true);
            CoordinateReferenceSystem origem = context.getAreaDeCorteCRS();

            if (origem != null && !CRS.equalsIgnoreMetadata(origem, alvo)) {
                MathTransform transform = CRS.findMathTransform(origem, alvo, true);
                estadoParana = JTS.transform(estadoParana, transform);
            }

            estadoParana.setSRID(SRID_SIRGAS_2000);

            // Geometria "preparada": o intersects() fica muito mais rápido com um polígono grande
            PreparedGeometry limite = PreparedGeometryFactory.prepare(estadoParana);

            List<SimpleFeature> recortadas = new ArrayList<>();

            for (SimpleFeature feature : context.getFeatures()) {

                Geometry geom = (Geometry) feature.getDefaultGeometry();

                if (geom == null || !limite.intersects(geom)) {
                    continue;
                }

                Geometry recorte = geom.intersection(estadoParana);

                if (recorte.isEmpty()) {
                    continue;
                }

                if (!recorte.isValid()) {
                    recorte = recorte.buffer(0);
                }

                recorte.setSRID(SRID_SIRGAS_2000);

                feature.setDefaultGeometry(recorte);

                recortadas.add(feature);
            }

            System.out.println("[Clip] Feições dentro do Paraná: "
                    + recortadas.size() + " de " + context.getFeatures().size());

            context.setFeatures(recortadas);

        } catch (Exception e) {

            // Antes o erro era engolido e o pipeline seguia com os dados SEM recorte
            throw new RuntimeException("Erro ao recortar pelo limite do Paraná: " + e.getMessage(), e);
        }

        context.setState(new DissolveState());
    }
}