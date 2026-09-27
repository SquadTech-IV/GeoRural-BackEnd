package br.com.fatec.georural.service.treatment.embargo.state;

import br.com.fatec.georural.service.treatment.embargo.Embargo;

public interface EmbargoState {

    public void execute(Embargo embargo);
    
}
