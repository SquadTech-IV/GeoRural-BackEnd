package br.com.fatec.georural.service.treatment.factory;

import java.io.File;

import org.springframework.stereotype.Component;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.GeoDatasetType;
import br.com.fatec.georural.service.treatment.embargo.EmbargoTreatment;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

@Component
public class TreatmentFactory {

    public BaseTreatment create(
            GeoDatasetType tipo,
            File file) {

        return switch (tipo) {

            case EMBARGO ->
                new EmbargoTreatment(file);

            case IMOVEL ->
                new PropertyAreaTreatment(file);
        };
    }
}