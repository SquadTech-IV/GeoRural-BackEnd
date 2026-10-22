package br.com.fatec.georural.service.treatment.embargo.state;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

public class StandardizationState implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {

        System.out.println(
                "[Standardization] Padronizando embargos..."
        );

        context.setState(
                new EmbargoSavingState()
        );
    }
}