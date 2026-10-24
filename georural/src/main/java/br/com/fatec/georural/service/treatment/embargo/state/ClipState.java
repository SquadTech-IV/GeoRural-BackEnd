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

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.service.treatment.audit.TransformacaoService;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.embargo.EmbargoTreatment;

public class ClipState implements TreatmentState {

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(BaseTreatment baseContext) {

        EmbargoTreatment context =
                (EmbargoTreatment) baseContext;

        Geometry estadoParana =
                context.getAreaDeCorte();

        if (estadoParana == null) {
            throw new IllegalStateException(
                    "Limite do Paraná não carregado");
        }

        try {

            CoordinateReferenceSystem alvo =
                    CRS.decode("EPSG:4674", true);

            CoordinateReferenceSystem origem =
                    context.getAreaDeCorteCRS();

            if (origem != null &&
                    !CRS.equalsIgnoreMetadata(
                            origem,
                            alvo)) {

                MathTransform transform =
                        CRS.findMathTransform(
                                origem,
                                alvo,
                                true);

                estadoParana =
                        JTS.transform(
                                estadoParana,
                                transform);
            }

            estadoParana.setSRID(
                    SRID_SIRGAS_2000);

            PreparedGeometry limite =
                    PreparedGeometryFactory.prepare(
                            estadoParana);

            List<SimpleFeature> recortadas =
                    new ArrayList<>();

            int entrada =
                    context.getFeatures().size();

            for (SimpleFeature feature :
                    context.getFeatures()) {

                Geometry geom =
                        (Geometry) feature
                                .getDefaultGeometry();

                if (geom == null ||
                        !limite.intersects(geom)) {
                    continue;
                }

                Geometry recorte =
                        geom.intersection(
                                estadoParana);

                if (recorte.isEmpty()) {
                    continue;
                }

                if (!recorte.isValid()) {
                    recorte = recorte.buffer(0);
                }

                recorte.setSRID(
                        SRID_SIRGAS_2000);

                feature.setDefaultGeometry(
                        recorte);

                recortadas.add(feature);
            }

            context.setFeatures(
                    recortadas);

            SpringContextHolder
                    .getBean(
                            TransformacaoService.class)
                    .registrar(
                            context.getExecucaoPipeline(),
                            3,
                            "CRUZAMENTO",
                            "Entrada="
                                    + entrada
                                    + ", saída="
                                    + recortadas.size());

        } catch (Exception e) {

            throw new RuntimeException(
                    "Erro ao recortar pelo limite do Paraná",
                    e);
        }

        context.setState(
                new DissolveState());
    }
}