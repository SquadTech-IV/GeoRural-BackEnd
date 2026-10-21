package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ConteudoRecebido;
import br.com.fatec.georural.dto.ingestao.FormatoRespostaApi;
import br.com.fatec.georural.dto.ingestao.RespostaApi;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LeitorRespostaApiTest {

    private final LeitorRespostaApi leitor = new LeitorRespostaApi();

    private RespostaApi resp(String corpo) {
        return new RespostaApi(200, "application/json", corpo.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void conta_features_de_um_geojson() {
        ConteudoRecebido r = leitor.ler(
                resp("{\"type\":\"FeatureCollection\",\"features\":[{},{},{}]}"),
                FormatoRespostaApi.GEOJSON);

        assertThat(r.quantidadeRegistros()).isEqualTo(3);
        assertThat(r.bemFormado()).isTrue();
    }

    @Test
    void json_invalido_fica_malformado() {
        ConteudoRecebido r = leitor.ler(resp("{nao e json"), FormatoRespostaApi.GEOJSON);

        assertThat(r.bemFormado()).isFalse();
        assertThat(r.quantidadeRegistros()).isZero();
    }

    @Test
    void conta_linhas_de_dados_do_csv() {
        ConteudoRecebido r = leitor.ler(resp("a,b\n1,2\n3,4\n5,6"), FormatoRespostaApi.CSV);

        assertThat(r.quantidadeRegistros()).isEqualTo(3); // 4 linhas - cabecalho
    }

    @Test
    void resposta_vazia_fica_malformada() {
        ConteudoRecebido r = leitor.ler(new RespostaApi(200, "application/json", new byte[0]),
                FormatoRespostaApi.GEOJSON);

        assertThat(r.quantidadeRegistros()).isZero();
        assertThat(r.tamanhoBytes()).isZero();
    }
}