<<<<<<< HEAD
package br.com.fatec.georural.service.treatment.embargo.state;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

public class DuplicityValidationState implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {

        Set<Object> seenSeqTad = new HashSet<>();
        List<SimpleFeature> uniqueFeatures = new ArrayList<>();

        for (SimpleFeature feature : context.getFeatures()) {

            Object seqTad = feature.getAttribute("seq_tad");

            if (seenSeqTad.add(seqTad)) {
                uniqueFeatures.add(feature);
            }
        }

        context.setFeatures(uniqueFeatures);
        context.setState(new ClipState());
    }
=======
package br.com.fatec.georural.service.treatment.embargo.state;

import java.util.HashSet;
import java.util.Set;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class DuplicityValidationState implements EmbargoState {

    @Override
    public void execute(Embargo context) {
        System.out.println("[4/8] Executando Validação de Duplicidade...");
        SimpleFeatureCollection inputCollection = context.getCurrentFeatures();
        DefaultFeatureCollection uniqueCollection = new DefaultFeatureCollection();
        Set<Object> seenSeqTad = new HashSet<>();

        try (SimpleFeatureIterator iterator = inputCollection.features()) {
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                Object seqTad = feature.getAttribute("seq_tad");

                if (seenSeqTad.add(seqTad)) {
                    uniqueCollection.add(feature);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro na remoção de duplicidades: " + e.getMessage(), e);
        }

        context.setCurrentFeatures(uniqueCollection);
        context.setState(new ClipState());
    }
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
}