package br.com.fatec.georural.service.treatment.reserva_legal.state;

import java.util.ArrayList;
import java.util.List;

import org.geotools.api.feature.simple.SimpleFeature;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

/**
 * Valida os campos da Reserva Legal e garante que exista um imóvel correspondente (pelo recibo = CAR)
 * já processado — a tabela RESERVA_LEGAL exige imo_id (NOT NULL). As feições sem recibo, sem geometria ou
 * sem imóvel correspondente são enviadas para a quarentena ANTES do salvamento.
 */
public class FieldsValidationState implements TreatmentState {

    @Override
    public void execute(BaseTreatment context) {
        ImovelRuralRepository imovelRepository = SpringContextHolder.getBean(ImovelRuralRepository.class);

        List<SimpleFeature> validadas = new ArrayList<>();
        int entrada = context.getFeatures().size();

        for (SimpleFeature feature : context.getFeatures()) {

            String recibo = lerRecibo(feature);

            if (recibo == null || recibo.isBlank()) {
                context.rejeitar("OBRIGATORIO", "recibo",
                        "Recibo (CAR) ausente na Reserva Legal", feature);
                continue;
            }

            if (feature.getDefaultGeometry() == null) {
                context.rejeitar("GEOMETRIA", "the_geom",
                        "Geometria ausente", feature);
                continue;
            }

            // A RL só pode ser gravada vinculada a um imóvel já existente (imo_id é obrigatório).
            if (imovelRepository.findByCodigoCar(recibo).isEmpty()) {
                context.rejeitar("INTEGRIDADE", "recibo",
                        "Imóvel (CAR " + recibo + ") não encontrado; processe o imóvel antes da Reserva Legal.",
                        feature);
                continue;
            }

            validadas.add(feature);
        }

        context.setFeatures(validadas);

        context.registrarTransformacao(
                "VALIDACAO_CAMPOS", entrada, validadas.size(),
                "rejeitadas=" + (entrada - validadas.size()));

        context.setState(new ReservaLegalSavingState());
    }

    private String lerRecibo(SimpleFeature feature) {
        Object recibo = feature.getAttribute("recibo");
        if (recibo == null) {
            recibo = feature.getAttribute("RECIBO");
        }
        return recibo != null ? recibo.toString().trim() : null;
    }
}
