package br.com.fatec.georural.services.treatment.embargo.state;

import br.com.fatec.georural.services.treatment.embargo.Embargo;

public interface EmbargoState {

    public void execute(Embargo embargo);
    
}
