package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ValidadorConjuntoDadosTest {

    private ConjuntoDadosRepository repositorio;
    private ValidadorConjuntoDados validador;

    @BeforeEach
    void setUp() {
        repositorio = Mockito.mock(ConjuntoDadosRepository.class);
        validador = new ValidadorConjuntoDados(repositorio);
    }

    @Test
    void validar_conjuntoInexistente_deveLancarRecursoNaoEncontrado() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> validador.validar(99L));
    }

    @Test
    void validar_semConjunto_deveLancarRequisicaoInvalidaSemConsultarOBanco() {
        assertThrows(RequisicaoInvalidaException.class, () -> validador.validar((Long) null));

        verifyNoInteractions(repositorio);
    }

    @ParameterizedTest
    @CsvSource({
            "RASCUNHO,UPLOAD",
            "EM_VALIDACAO,UPLOAD",
            "PUBLICADO,UPLOAD",
            "RASCUNHO,AMBOS",
            "PUBLICADO,AMBOS"
    })
    void validar_conjuntoElegivel_deveRetornarOConjunto(String situacao, String formaEntrada) {
        ConjuntoDados conjunto = conjunto(1L, situacao, formaEntrada);
        when(repositorio.findById(1L)).thenReturn(Optional.of(conjunto));

        assertSame(conjunto, validador.validar(1L));
    }

    @ParameterizedTest
    @CsvSource({
            "ARQUIVADO,UPLOAD",
            "arquivado,AMBOS"
    })
    void validar_conjuntoArquivado_deveLancarRequisicaoInvalida(String situacao, String formaEntrada) {
        when(repositorio.findById(1L)).thenReturn(Optional.of(conjunto(1L, situacao, formaEntrada)));

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validar(1L));
    }

    @Test
    void validar_conjuntoSoDeApi_deveLancarRequisicaoInvalida() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(conjunto(1L, "PUBLICADO", "API")));

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validar(1L));
    }

    @Test
    void validar_conjuntoSemFormaDeEntrada_deveLancarRequisicaoInvalida() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(conjunto(1L, "PUBLICADO", null)));

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validar(1L));
    }

    private static ConjuntoDados conjunto(Long id, String situacao, String formaEntrada) {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(id);
        conjunto.setSituacao(situacao);
        conjunto.setFormaEntrada(formaEntrada);
        return conjunto;
    }
}