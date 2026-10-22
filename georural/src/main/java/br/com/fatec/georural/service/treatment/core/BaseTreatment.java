package br.com.fatec.georural.service.treatment.core;

import java.io.File;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.Versao;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseTreatment {

    private TreatmentState state;

    private List<SimpleFeature> features;

    private ArquivoBruto arquivoBruto;

    private Versao versao;

    private File file;

    private CoordinateReferenceSystem targetCRS;

    public void execute() {

        while (state != null) {

            TreatmentState atual = state;

            atual.execute(this);

            // Estados compartilhados (common.state) não sabem quem vem depois.
            // Se o estado não escolheu o próximo, quem decide é o tratamento.
            // Sem isso o mesmo estado seria executado para sempre.
            if (state == atual) {
                state = proximoApos(atual);
            }
        }
    }

    /**
     * Próximo estado para quem não define o seu próprio.
     * Retornar null encerra o pipeline.
     */
    protected abstract TreatmentState proximoApos(TreatmentState atual);
}