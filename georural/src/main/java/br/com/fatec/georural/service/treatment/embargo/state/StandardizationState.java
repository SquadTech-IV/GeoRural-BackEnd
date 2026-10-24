<<<<<<< HEAD
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
=======
package br.com.fatec.georural.service.treatment.embargo.state;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class StandardizationState implements EmbargoState {

    @Override
    public void execute(Embargo context) {
        System.out.println("[7/8] Executando Padronização das Propriedades de Embargo...");
        
        // Encaminha a coleção tratada para o estado de salvamento
        context.setState(new EmbargoSavingState());
    }
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
}