package br.com.fatec.georural.services.treatment.property_area;

import java.io.File;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.services.treatment.property_area.state.GeometryValidationState;
import br.com.fatec.georural.services.treatment.property_area.state.PropertyAreaState;
import lombok.Getter;
import lombok.Setter;

@Getter
public class PropertyArea {

    @Setter
    private PropertyAreaState state = new GeometryValidationState();

    private final File propertyAreaFile;

    @Setter
    private List<SimpleFeature> features;

    public PropertyArea(File propertyAreaFile) {
        this.propertyAreaFile = propertyAreaFile;
    }

    public void execute() {
        state.execute(this);
    }
}
