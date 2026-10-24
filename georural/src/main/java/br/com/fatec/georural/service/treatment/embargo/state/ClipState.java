<<<<<<< HEAD
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
=======
package br.com.fatec.georural.service.treatment.embargo.state;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class ClipState implements EmbargoState {

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(Embargo context) {
        System.out.println("[5/8] Executando Recorte (Clip para Estado do Paraná)...");
        Geometry estadoParana = context.getAreaDeCorte();
        SimpleFeatureCollection inputCollection = context.getCurrentFeatures();
        DefaultFeatureCollection clippedCollection = new DefaultFeatureCollection();

        if (inputCollection == null || inputCollection.isEmpty()) {
            System.out.println("Aviso: Nenhuma feição recebida no ClipState.");
            context.setState(new DissolveState());
            return;
        }

        try (SimpleFeatureIterator iterator = inputCollection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Geometry geom = (Geometry) feature.getDefaultGeometry();

                if (geom != null) {
                    // Se não tiver área de corte ou se interceptar o estado do Paraná
                    if (estadoParana == null || geom.intersects(estadoParana)) {
                        Geometry clippedGeom = (estadoParana != null) ? geom.intersection(estadoParana) : geom;

                        if (clippedGeom != null && !clippedGeom.isEmpty()) {
                            if (!clippedGeom.isValid()) {
                                clippedGeom = clippedGeom.buffer(0);
                            }
                            clippedGeom.setSRID(SRID_SIRGAS_2000);
                            feature.setDefaultGeometry(clippedGeom);
                            clippedCollection.add(feature);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro no Clip: " + e.getMessage() + ". Prosseguindo com coleção original.");
            clippedCollection = (DefaultFeatureCollection) inputCollection;
        }

        System.out.println("Feições restantes após Clip: " + clippedCollection.size());
        context.setCurrentFeatures(clippedCollection);
        context.setState(new DissolveState());
    }
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
}