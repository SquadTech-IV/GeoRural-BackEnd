package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ConteudoRecebido;
import br.com.fatec.georural.dto.ingestao.FormatoRespostaApi;
import br.com.fatec.georural.dto.ingestao.RespostaApi;
import br.com.fatec.georural.dto.ingestao.ResultadoValidacao;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ValidadorRecebimentoApiTest {

    private final ValidadorRecebimentoApi validador = new ValidadorRecebimentoApi();

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void aceita_resposta_valida() {
        byte[] b = bytes("{\"type\":\"FeatureCollection\",\"features\":[{}]}");
        RespostaApi resp = new RespostaApi(200, "application/geo+json", b);
        ConteudoRecebido cont = new ConteudoRecebido(FormatoRespostaApi.GEOJSON, 10, b.length, true, b);

        ResultadoValidacao r = validador.validar(resp, cont, FormatoRespostaApi.GEOJSON);

        assertThat(r.valido()).isTrue();
    }

    @Test
    void rejeita_resposta_vazia() {
        RespostaApi resp = new RespostaApi(200, "application/geo+json", new byte[0]);
        ConteudoRecebido cont = new ConteudoRecebido(FormatoRespostaApi.GEOJSON, 0, 0, false, new byte[0]);

        ResultadoValidacao r = validador.validar(resp, cont, FormatoRespostaApi.GEOJSON);

        assertThat(r.valido()).isFalse();
        assertThat(r.resumo().toLowerCase()).contains("vazia");
    }

    @Test
    void rejeita_status_http_de_erro() {
        byte[] b = bytes("not found");
        RespostaApi resp = new RespostaApi(404, "text/plain", b);
        ConteudoRecebido cont = new ConteudoRecebido(FormatoRespostaApi.GEOJSON, 0, b.length, false, b);

        ResultadoValidacao r = validador.validar(resp, cont, FormatoRespostaApi.GEOJSON);

        assertThat(r.valido()).isFalse();
        assertThat(r.resumo()).contains("404");
    }

    @Test
    void rejeita_sem_registros() {
        byte[] b = bytes("{\"type\":\"FeatureCollection\",\"features\":[]}");
        RespostaApi resp = new RespostaApi(200, "application/geo+json", b);
        ConteudoRecebido cont = new ConteudoRecebido(FormatoRespostaApi.GEOJSON, 0, b.length, true, b);

        ResultadoValidacao r = validador.validar(resp, cont, FormatoRespostaApi.GEOJSON);

        assertThat(r.valido()).isFalse();
    }
}