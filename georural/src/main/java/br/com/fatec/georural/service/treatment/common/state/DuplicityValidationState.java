package br.com.fatec.georural.service.treatment.common.state;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.DuplicidadeMemoriaService;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import org.geotools.api.feature.simple.SimpleFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado que executa a validação de duplicidade (VAL-004) de forma eficiente em memória (US#08 - T06).
 */
public class DuplicityValidationState implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {
        DuplicidadeMemoriaService service = SpringContextHolder.getBean(DuplicidadeMemoriaService.class);
        DuplicidadeMemoriaService.RastreadorDuplicidade rastreador = service.criarRastreador();

        List<SimpleFeature> uniqueFeatures = new ArrayList<>();
        int entrada = context.getFeatures().size();

        for (SimpleFeature feature : context.getFeatures()) {
            String chave = service.extrairChaveIdentificadora(feature);

            if (chave != null && rastreador.adicionarEVerificarDuplicado(chave)) {
                context.rejeitar(
                        "DUPLICIDADE",
                        "chave_identificadora",
                        "Registro duplicado detectado com a chave/hash: " + chave,
                        feature
                );
                continue;
            }

            uniqueFeatures.add(feature);
        }

        context.setFeatures(uniqueFeatures);

        context.registrarTransformacao(
                "VALIDACAO_DUPLICIDADE",
                entrada,
                uniqueFeatures.size(),
                "duplicadas=" + (entrada - uniqueFeatures.size())
        );
    }
}