package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.ArquivoDuplicadoException;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VerificadorDuplicidadeTest {

    private static final String HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String COMPETENCIA = "2026-10";

    private ArquivoBrutoRepository repositorio;
    private VerificadorDuplicidade verificador;

    @BeforeEach
    void setUp() {
        repositorio = Mockito.mock(ArquivoBrutoRepository.class);
        verificador = new VerificadorDuplicidade(repositorio);
        ReflectionTestUtils.setField(verificador, "prazoAbandonoMinutos", 15L);
    }

    @Test
    void verificar_semRegistroComAIdentidade_deveRetornarVazio() {
        when(repositorio.buscarPorIdentidade(1L, COMPETENCIA, HASH)).thenReturn(Optional.empty());
        when(repositorio.buscarMesmoConteudoNoConjunto(1L, HASH)).thenReturn(List.of());

        assertTrue(verificador.verificar(1L, COMPETENCIA, HASH).isEmpty());
    }

    @Test
    void verificar_registroRecebido_deveLancarDuplicado() {
        when(repositorio.buscarPorIdentidade(1L, COMPETENCIA, HASH))
                .thenReturn(Optional.of(registro(1L, "RECEBIDO", LocalDateTime.now().minusDays(3))));

        assertThrows(ArquivoDuplicadoException.class, () -> verificador.verificar(1L, COMPETENCIA, HASH));
    }

    @Test
    void verificar_mesmoHashEmOutraCompetencia_devePermitir() {
        when(repositorio.buscarPorIdentidade(1L, COMPETENCIA, HASH)).thenReturn(Optional.empty());
        when(repositorio.buscarMesmoConteudoNoConjunto(1L, HASH))
                .thenReturn(List.of(registro(1L, "RECEBIDO", LocalDateTime.now().minusDays(30))));

        assertTrue(verificador.verificar(1L, COMPETENCIA, HASH).isEmpty());
    }

    @Test
    void verificar_recebendoDentroDoPrazo_deveLancarDuplicado() {
        when(repositorio.buscarPorIdentidade(1L, COMPETENCIA, HASH))
                .thenReturn(Optional.of(registro(1L, "RECEBENDO", LocalDateTime.now().minusMinutes(2))));

        assertThrows(ArquivoDuplicadoException.class, () -> verificador.verificar(1L, COMPETENCIA, HASH));
    }

    @Test
    void verificar_recebendoAbandonadoDoMesmoConjunto_deveDevolverParaRetomada() {
        ArquivoBruto abandonado = registro(1L, "RECEBENDO", LocalDateTime.now().minusMinutes(16));
        when(repositorio.buscarPorIdentidade(1L, COMPETENCIA, HASH)).thenReturn(Optional.of(abandonado));

        assertSame(abandonado, verificador.verificar(1L, COMPETENCIA, HASH).orElseThrow());
    }

    @Test
    void verificar_deveNormalizarOHashAntesDeConsultar() {
        when(repositorio.buscarPorIdentidade(1L, COMPETENCIA, HASH)).thenReturn(Optional.empty());
        when(repositorio.buscarMesmoConteudoNoConjunto(1L, HASH)).thenReturn(List.of());

        verificador.verificar(1L, COMPETENCIA, "  " + HASH.toUpperCase() + "  ");

        verify(repositorio).buscarPorIdentidade(1L, COMPETENCIA, HASH);
    }

    @Test
    void verificar_deveInformarConjuntoEHashNaExcecao() {
        when(repositorio.buscarPorIdentidade(1L, COMPETENCIA, HASH))
                .thenReturn(Optional.of(registro(1L, "RECEBIDO", LocalDateTime.now())));

        ArquivoDuplicadoException erro = assertThrows(
                ArquivoDuplicadoException.class, () -> verificador.verificar(1L, COMPETENCIA, HASH));

        assertEquals(1L, erro.getConjuntoId());
        assertEquals(HASH, erro.getHash());
    }

    @Test
    void verificar_argumentosAusentes_deveLancarIllegalArgumentSemConsultar() {
        assertThrows(IllegalArgumentException.class, () -> verificador.verificar(null, COMPETENCIA, HASH));
        assertThrows(IllegalArgumentException.class, () -> verificador.verificar(1L, null, HASH));
        assertThrows(IllegalArgumentException.class, () -> verificador.verificar(1L, "  ", HASH));
        assertThrows(IllegalArgumentException.class, () -> verificador.verificar(1L, COMPETENCIA, null));
        assertThrows(IllegalArgumentException.class, () -> verificador.verificar(1L, COMPETENCIA, "  "));

        verifyNoInteractions(repositorio);
    }

    private static ArquivoBruto registro(Long conjuntoId, String status, LocalDateTime dataUpload) {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(conjuntoId);

        ArquivoBruto registro = new ArquivoBruto();
        registro.setId(10L);
        registro.setConjunto(conjunto);
        registro.setCompetencia("2026-09");
        registro.setHash(HASH);
        registro.setStatus(status);
        registro.setDataUpload(dataUpload);
        return registro;
    }
}