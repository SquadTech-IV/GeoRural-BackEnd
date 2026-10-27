package br.com.fatec.georural.service.treatment.embargo.state;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

public class DuplicityValidationState
        implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {

        Set<Object> seenSeqTad = new HashSet<>();

        List<SimpleFeature> uniqueFeatures = new ArrayList<>();

        int entrada = context.getFeatures().size();

        for (SimpleFeature feature : context.getFeatures()) {

            Object seqTad = feature.getAttribute("seq_tad");
            if (seqTad == null) {
                seqTad = feature.getAttribute("SEQ_TAD");
            }

            // Sem seq_tad não há como aferir duplicidade. NÃO tratamos null/vazio como chave:
            // senão a 1ª feição sem seq_tad "consome" o null no Set e TODAS as demais seriam
            // rejeitadas como duplicadas (zerando o lote). A unicidade real do embargo é o
            // num_tad, consolidado adiante no DissolveState.
            if (seqTad == null || seqTad.toString().isBlank()) {
                uniqueFeatures.add(feature);
                continue;
            }

            if (!seenSeqTad.add(seqTad)) {
                context.rejeitar("DUPLICIDADE", "seq_tad",
                        "seq_tad duplicado: " + seqTad, feature);
                continue;
            }

            uniqueFeatures.add(feature);
        }

        context.setFeatures(uniqueFeatures);

        context.registrarTransformacao(
                "VALIDACAO_DUPLICIDADE",
                entrada,
                uniqueFeatures.size(),
                "duplicadas=" + (entrada - uniqueFeatures.size()));

        context.setState(new ClipState());
    }
}
