<<<<<<< HEAD
package br.com.fatec.georural.service.treatment.embargo.state;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.union.UnaryUnionOp;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

/**
 * Une os pedaços de um MESMO embargo (mesmo num_tad). Embargos diferentes continuam
 * separados, para não perder a rastreabilidade (cada linha da tabela EMBARGO é um num_tad).
 *
 * Nos dados atuais (2.158 embargos do Paraná) o num_tad é único, então hoje este passo
 * não altera nada, mas protege o cálculo caso um embargo venha em mais de uma linha.
 * A união entre embargos diferentes que se sobrepõem é feita na consulta do IAE
 * (SDO_AGGR_UNION), por imóvel.
 */
public class DissolveState implements TreatmentState {

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(BaseTreatment context) {

        Map<String, List<SimpleFeature>> grupos = new LinkedHashMap<>();

        for (SimpleFeature feature : context.getFeatures()) {

            String chave = String.valueOf(feature.getAttribute("num_tad"));

            grupos.computeIfAbsent(chave, k -> new ArrayList<>()).add(feature);
        }

        List<SimpleFeature> resultado = new ArrayList<>(grupos.size());

        int unidos = 0;

        for (List<SimpleFeature> grupo : grupos.values()) {

            SimpleFeature base = grupo.get(0);

            if (grupo.size() > 1) {

                List<Geometry> geometrias = new ArrayList<>();

                for (SimpleFeature feature : grupo) {
                    geometrias.add((Geometry) feature.getDefaultGeometry());
                }

                Geometry uniao = UnaryUnionOp.union(geometrias);

                uniao.setSRID(SRID_SIRGAS_2000);

                base.setDefaultGeometry(uniao);

                unidos++;
            }

            resultado.add(base);
        }

        System.out.println("[Dissolve] " + context.getFeatures().size() + " feições em "
                + resultado.size() + " embargos (" + unidos + " com partes unidas).");

        context.setFeatures(resultado);

        context.setState(new StandardizationState());
    }
=======
package br.com.fatec.georural.service.treatment.embargo.state;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class DissolveState implements EmbargoState {

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(Embargo context) {
        System.out.println("[6/8] Executando Dissolve...");
        SimpleFeatureCollection inputCollection = context.getCurrentFeatures();

        if (inputCollection == null || inputCollection.isEmpty()) {
            System.out.println("Coleção vazia no Dissolve. Avançando...");
            context.setState(new StandardizationState());
            return;
        }

        DefaultFeatureCollection outputCollection = new DefaultFeatureCollection();

        try (SimpleFeatureIterator iterator = inputCollection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Geometry geom = (Geometry) feature.getDefaultGeometry();

                if (geom != null && !geom.isEmpty()) {
                    if (!geom.isValid()) {
                        geom = geom.buffer(0);
                    }
                    geom.setSRID(SRID_SIRGAS_2000);
                    feature.setDefaultGeometry(geom);
                    outputCollection.add(feature);
                }
            }
        } catch (Exception e) {
            System.err.println("Erro no Dissolve: " + e.getMessage());
        }

        System.out.println("Feições após Dissolve: " + outputCollection.size());
        context.setCurrentFeatures(outputCollection);
        context.setState(new StandardizationState());
    }
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
}