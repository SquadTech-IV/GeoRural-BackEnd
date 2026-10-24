package br.com.fatec.georural.service;

import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.entity.ResultadoMunicipio;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.exception.ConflitoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImutabilidadeVersaoServiceTest {

    private final ImutabilidadeVersaoService service = new ImutabilidadeVersaoService();

    private Versao versao(String situacao) {
        Versao v = new Versao();
        v.setId(7L);
        v.setSituacao(situacao);
        return v;
    }

    @Test
    void imutavel_deveSerFalso_quandoRascunho() {
        assertThat(service.imutavel(versao("RASCUNHO"))).isFalse();
    }

    @Test
    void imutavel_deveSerVerdadeiro_quandoPublicada() {
        assertThat(service.imutavel(versao("VIGENTE"))).isTrue();
        assertThat(service.imutavel(versao("ANTERIOR"))).isTrue();
    }

    @Test
    void garantirVersaoEditavel_devePermitir_quandoRascunho() {
        assertThatCode(() -> service.garantirVersaoEditavel(versao("RASCUNHO")))
                .doesNotThrowAnyException();
    }

    @Test
    void garantirVersaoEditavel_deveBloquear_quandoVigente() {
        assertThatThrownBy(() -> service.garantirVersaoEditavel(versao("VIGENTE")))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void garantirVersaoEditavel_deveBloquear_quandoAnterior() {
        assertThatThrownBy(() -> service.garantirVersaoEditavel(versao("ANTERIOR")))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void garantirEditavel_deveBloquear_quandoResultadoIndicadorDeVersaoPublicada() {
        ResultadoIndicador resultado = new ResultadoIndicador();
        resultado.setId(3L);
        resultado.setVersao(versao("VIGENTE"));

        assertThatThrownBy(() -> service.garantirEditavel(resultado))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    void garantirEditavel_devePermitir_quandoResultadoIndicadorDeRascunho() {
        ResultadoIndicador resultado = new ResultadoIndicador();
        resultado.setId(3L);
        resultado.setVersao(versao("RASCUNHO"));

        assertThatCode(() -> service.garantirEditavel(resultado)).doesNotThrowAnyException();
    }

    @Test
    void garantirEditavel_deveBloquear_quandoResultadoMunicipioDeVersaoPublicada() {
        ResultadoMunicipio resultado = new ResultadoMunicipio();
        resultado.setId(4L);
        resultado.setVersao(versao("ANTERIOR"));

        assertThatThrownBy(() -> service.garantirEditavel(resultado))
                .isInstanceOf(ConflitoException.class);
    }
}