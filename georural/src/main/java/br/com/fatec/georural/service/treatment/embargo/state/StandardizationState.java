package br.com.fatec.georural.service.treatment.embargo.state;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

public class StandardizationState implements EmbargoState {

    @Override
    public void execute(Embargo context) {
        System.out.println("[7/8] Executando Padronização das Propriedades de Embargo...");
        
        // Encaminha a coleção tratada para o estado de salvamento
        context.setState(new EmbargoSavingState());
    }
}