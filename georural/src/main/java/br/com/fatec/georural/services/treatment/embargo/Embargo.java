package br.com.fatec.georural.services.treatment.embargo;

import java.io.File;

import br.com.fatec.georural.services.treatment.embargo.state.EmbargoState;
import br.com.fatec.georural.services.treatment.embargo.state.GeometryValidationState;
import lombok.Getter;
import lombok.Setter;

@Getter()
public class Embargo {

    @Setter 
    private EmbargoState state = new GeometryValidationState();
    private File embargoFile;

    public Embargo(File embargoFile) {
        this.embargoFile = embargoFile;
    }

}