package br.com.fatec.georural.services.treatment.property_area;

import java.io.File;

import br.com.fatec.georural.services.treatment.property_area.state.GeometryValidationState;
import br.com.fatec.georural.services.treatment.property_area.state.PropertyAreaState;
import lombok.Getter;
import lombok.Setter;

@Getter()
public class PropertyArea {

    @Setter 
    private PropertyAreaState state = new GeometryValidationState();
    private File propertyAreaFile;

    public PropertyArea(File propertyAreaFile) {
        this.propertyAreaFile = propertyAreaFile;
    }

}
