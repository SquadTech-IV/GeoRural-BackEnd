package br.com.fatec.georural.service.treatment.property_area;

import java.io.File;
import java.util.Map;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.dto.request.regra.ProcessedProperty;
import br.com.fatec.georural.service.treatment.common.state.GeometryValidationState;
import br.com.fatec.georural.service.treatment.common.state.ReprojectionState;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.state.ClipState;
import br.com.fatec.georural.service.treatment.property_area.state.DissolveState;
import br.com.fatec.georural.service.treatment.property_area.state.FieldsValidationState;
import br.com.fatec.georural.service.treatment.property_area.state.MetadataResolutionState;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PropertyAreaTreatment extends BaseTreatment {

    private final File propertyAreaFile;

    /** Limite do Paraná (mesmo usado no fluxo de embargo) - US09.01. */
    private Geometry areaDeCorte;

    private CoordinateReferenceSystem areaDeCorteCRS;

    private Map<String, Geometry> dissolvedGeometries;

    private Map<String, ProcessedProperty> processedProperties;

    private Map<String, String> municipiosPorRecibo;

    public PropertyAreaTreatment(File propertyAreaFile) {
        this.propertyAreaFile = propertyAreaFile;
        this.setState(new MetadataResolutionState());
    }

    @Override
    protected TreatmentState proximoApos(TreatmentState atual) {

        if (atual instanceof GeometryValidationState) {
            return new FieldsValidationState();
        }

        if (atual instanceof ReprojectionState) {
            return new ClipState();
        }

        if (atual instanceof ClipState) {
            return new DissolveState();
        }

        return null;
    }
}
