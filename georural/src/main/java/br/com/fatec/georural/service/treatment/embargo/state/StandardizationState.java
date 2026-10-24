package br.com.fatec.georural.service.treatment.embargo.state;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.service.treatment.audit.TransformacaoService;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

public class StandardizationState
        implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {

        SpringContextHolder
                .getBean(
                        TransformacaoService.class)
                .registrar(
                        context.getExecucaoPipeline(),
                        5,
                        "PADRONIZACAO",
                        "Padronização concluída");

        context.setState(
                new EmbargoSavingState());
    }
}