package br.com.fatec.georural.service.treatment.property_area;

import java.io.File;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.service.treatment.property_area.state.MetadataResolutionState;
import br.com.fatec.georural.service.treatment.property_area.state.PropertyAreaState;
import lombok.Getter;
import lombok.Setter;

@Getter
public class PropertyArea {

    @Setter
    private PropertyAreaState state = new MetadataResolutionState();

    private final File propertyAreaFile;

    @Setter
    private List<SimpleFeature> features;

    @Setter
    private ArquivoBruto arquivoBruto;

    @Setter
    private Versao versao;

    public PropertyArea(File propertyAreaFile) {
        this.propertyAreaFile = propertyAreaFile;
    }

    public void execute() {
        state.execute(this);
    }
}