package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.regra.ParametroInput;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParametroRegraValidadorTest {

    private final ParametroRegraValidador validador = new ParametroRegraValidador();

    @Test
    void naoDeveFalhar_quandoListaNula() {
        assertThatCode(() -> validador.validar(null)).doesNotThrowAnyException();
    }

    @Test
    void percentual_deveAceitar_entre0e100() {
        assertThatCode(() -> validador.validar(List.of(p("x", "PERCENTUAL", "50"))))
                .doesNotThrowAnyException();
    }

    @Test
    void percentual_deveRejeitar_acimaDe100() {
        assertThatThrownBy(() -> validador.validar(List.of(p("x", "PERCENTUAL", "150"))))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void numero_deveRejeitar_zeroOuNegativo() {
        assertThatThrownBy(() -> validador.validar(List.of(p("x", "NUMERO", "0"))))
                .isInstanceOf(RequisicaoInvalidaException.class);
        assertThatThrownBy(() -> validador.validar(List.of(p("x", "NUMERO", "-5"))))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void numero_deveAceitar_positivo() {
        assertThatCode(() -> validador.validar(List.of(p("x", "NUMERO", "1000"))))
                .doesNotThrowAnyException();
    }

    @Test
    void data_deveAceitar_isoValida() {
        assertThatCode(() -> validador.validar(List.of(p("x", "DATA", "2008-07-22"))))
                .doesNotThrowAnyException();
    }

    @Test
    void data_deveRejeitar_invalida() {
        assertThatThrownBy(() -> validador.validar(List.of(p("x", "DATA", "2020-13-40"))))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void deveRejeitar_tipoDesconhecido() {
        assertThatThrownBy(() -> validador.validar(List.of(p("x", "COR", "azul"))))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void deveRejeitar_valorVazio() {
        assertThatThrownBy(() -> validador.validar(List.of(p("x", "TEXTO", "   "))))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    private ParametroInput p(String nome, String tipo, String valor) {
        return new ParametroInput(nome, tipo, null, valor);
    }
}