package br.com.fatec.georural.service.treatment.embargo;

import java.io.File;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.common.state.GeometryValidationState;
import br.com.fatec.georural.service.treatment.common.state.ReprojectionState;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.embargo.state.FieldsValidationState;
import br.com.fatec.georural.service.treatment.embargo.state.MetadataResolutionState;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmbargoTreatment extends BaseTreatment {

    private final File embargoFile;

    private Geometry areaDeCorte;

    private CoordinateReferenceSystem areaDeCorteCRS;

    public EmbargoTreatment(File embargoFile) {

        this.embargoFile = embargoFile;

        this.setState(new MetadataResolutionState());
    }

    @Override
    protected TreatmentState proximoApos(TreatmentState atual) {

        if (atual instanceof GeometryValidationState) {
            return new ReprojectionState();
        }

        if (atual instanceof ReprojectionState) {
            return new FieldsValidationState();
        }

        return null;
    }
}