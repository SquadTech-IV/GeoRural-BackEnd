package br.com.fatec.georural.service.treatment.orchestrator;

import java.io.File;

import org.springframework.stereotype.Service;

import br.com.fatec.georural.service.treatment.DetectorTipoCamada;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.GeoDatasetType;
import br.com.fatec.georural.service.treatment.factory.TreatmentFactory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TreatmentOrchestrator {

    private final DetectorTipoCamada detector;

    private final TreatmentFactory factory;

    public void execute(File file) {

        GeoDatasetType tipo =
                detector.detectar(file);

        BaseTreatment treatment =
                factory.create(tipo, file);

        treatment.execute();
    }
}
