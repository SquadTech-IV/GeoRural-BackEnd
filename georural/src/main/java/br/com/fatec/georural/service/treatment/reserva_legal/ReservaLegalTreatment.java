package br.com.fatec.georural.service.treatment.reserva_legal;

import java.io.File;

import br.com.fatec.georural.service.treatment.common.state.GeometryValidationState;
import br.com.fatec.georural.service.treatment.common.state.ReprojectionState;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.reserva_legal.state.FieldsValidationState;
import br.com.fatec.georural.service.treatment.reserva_legal.state.MetadataResolutionState;
import lombok.Getter;
import lombok.Setter;

/**
 * Tratamento da camada de Reserva Legal (SICAR).
 *
 * <p>Cadeia de estados:
 * <pre>
 *   MetadataResolution (reusa o contexto do orquestrador: arquivo bruto real, conjunto e execução)
 *     -> GeometryValidation (comum)
 *     -> Reprojection (comum, EPSG:4674)
 *     -> FieldsValidation (recibo presente + imóvel correspondente já processado)
 *     -> ReservaLegalSaving (grava RESERVA_LEGAL ligada ao imóvel pelo CAR)
 * </pre>
 *
 * <p>A RL se liga ao {@code IMOVEL_RURAL} pelo recibo (= código do CAR), então o imóvel precisa ter sido
 * processado antes. As feições sem imóvel correspondente vão para a quarentena.
 */
@Getter
@Setter
public class ReservaLegalTreatment extends BaseTreatment {

    private final File reservaLegalFile;

    public ReservaLegalTreatment(File reservaLegalFile) {
        this.reservaLegalFile = reservaLegalFile;
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
