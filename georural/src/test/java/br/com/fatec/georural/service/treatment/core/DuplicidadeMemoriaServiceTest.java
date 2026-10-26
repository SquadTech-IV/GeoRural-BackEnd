package br.com.fatec.georural.service.treatment.core;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuplicidadeMemoriaServiceTest {

    private final DuplicidadeMemoriaService service = new DuplicidadeMemoriaService();

    @Test
    @DisplayName("VAL-004: Deve identificar e rejeitar duplicidades consecutivas usando o rastreador")
    void deveIdentificarDuplicidade() {
        DuplicidadeMemoriaService.RastreadorDuplicidade rastreador = service.criarRastreador();

        SimpleFeature f1 = criarFeature("RECIBO-001", "TEMA-A");
        SimpleFeature f2 = criarFeature("RECIBO-001", "TEMA-A");

        String chave1 = service.extrairChaveIdentificadora(f1);
        String chave2 = service.extrairChaveIdentificadora(f2);

        assertFalse(rastreador.adicionarEVerificarDuplicado(chave1));
        assertTrue(rastreador.adicionarEVerificarDuplicado(chave2));
    }

    private SimpleFeature criarFeature(String recibo, String tema) {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("FeatureImovel");
        typeBuilder.add("recibo", String.class);
        typeBuilder.add("tema", String.class);

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(typeBuilder.buildFeatureType());
        builder.add(recibo);
        builder.add(tema);
        return builder.buildFeature("1");
    }
}