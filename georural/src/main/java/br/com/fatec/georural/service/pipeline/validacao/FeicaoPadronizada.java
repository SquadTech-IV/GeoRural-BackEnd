package br.com.fatec.georural.service.pipeline.validacao;

import org.locationtech.jts.geom.Geometry;

import java.util.List;
import java.util.Map;

// feicao com os campos ja traduzidos para o esquema do GeoRural
public record FeicaoPadronizada(
        String identificador,
        long linha,
        Map<String, Object> atributos,
        Map<String, Object> atributosOriginais,
        Geometry geometria,
        List<ProblemaFeicao> problemas) {

    public boolean rejeitada() {
        return problemas.stream().anyMatch(ProblemaFeicao::rejeita);
    }

    public List<ProblemaFeicao> avisos() {
        return problemas.stream().filter(p -> !p.rejeita()).toList();
    }

    public List<ProblemaFeicao> erros() {
        return problemas.stream().filter(ProblemaFeicao::rejeita).toList();
    }
}
