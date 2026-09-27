package br.com.fatec.georural.service.treatment.embargo;

import java.io.File;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.service.treatment.embargo.state.EmbargoState;
import br.com.fatec.georural.service.treatment.embargo.state.MetadataResolutionState;
import lombok.Getter;
import lombok.Setter;

@Getter
public class Embargo {

    @Setter
    private EmbargoState state = new MetadataResolutionState();

    private final File embargoFile;

    @Setter
    private Geometry areaDeCorte;

    @Setter
    private CoordinateReferenceSystem areaDeCorteCRS;

    @Setter
    private CoordinateReferenceSystem targetCRS;

    @Setter
    private SimpleFeatureCollection currentFeatures;

    @Setter
    private List<SimpleFeature> features;

    @Setter
    private ArquivoBruto arquivoBruto;

    @Setter
    private Versao versao;

    public Embargo(File embargoFile) {
        this.embargoFile = embargoFile;
        try {
            // true = força ordem tradicional de eixos (longitude/X primeiro),
            // evitando a inversão lat/lon que tivemos antes
            this.targetCRS = CRS.decode("EPSG:4674", true);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar o CRS alvo EPSG:4674", e);
        }
    }

    public void execute() {
        while (state != null) {
            state.execute(this);
        }
    }
}