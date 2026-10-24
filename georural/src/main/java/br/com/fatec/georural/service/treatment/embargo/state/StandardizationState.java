package br.com.fatec.georural.service.treatment.embargo.state;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;

/**
 * Padronização do embargo (antes só registrava "concluída" sem fazer nada):
 * - textos: trim e vazio vira null;
 * - num_tad: caixa alta;
 * - geometria: SRID 4674 (SIRGAS 2000).
 */
public class StandardizationState
        implements TreatmentState {

    private static final int SRID_SIRGAS_2000 = 4674;

    @Override
    public void execute(BaseTreatment context) {

        int total = context.getFeatures().size();

        for (SimpleFeature feature : context.getFeatures()) {

            for (AttributeDescriptor d : feature.getFeatureType().getAttributeDescriptors()) {

                String nome = d.getLocalName();

                Object valor = feature.getAttribute(nome);

                if (valor instanceof String texto) {

                    String limpo = texto.trim();

                    feature.setAttribute(nome, limpo.isEmpty() ? null : limpo);
                }
            }

            Object numTad = feature.getAttribute("num_tad");

            if (numTad instanceof String s) {
                feature.setAttribute("num_tad", s.toUpperCase());
            }

            Object geom = feature.getDefaultGeometry();

            if (geom instanceof Geometry g) {
                g.setSRID(SRID_SIRGAS_2000);
            }
        }

        context.registrarTransformacao(
                "PADRONIZACAO",
                total,
                total,
                "textos com trim, num_tad em caixa alta, SRID=" + SRID_SIRGAS_2000);

        context.setState(new EmbargoSavingState());
    }
}
