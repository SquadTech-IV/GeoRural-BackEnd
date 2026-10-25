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

    private ArquivoBrutoRepository repositorio;
    private VerificadorDuplicidade verificador;

    @BeforeEach
    void setUp() {
        repositorio = Mockito.mock(ArquivoBrutoRepository.class);
        verificador = new VerificadorDuplicidade(repositorio);
        // Sem o contexto do Spring o @Value não é aplicado; sem este valor todo RECEBENDO seria "abandonado".
        ReflectionTestUtils.setField(verificador, "prazoAbandonoMinutos", 15L);
    }

    @Test
    void verificar_semRegistroComOHash_deveRetornarVazio() {
        when(repositorio.findByHash(HASH)).thenReturn(Optional.empty());

        assertTrue(verificador.verificar(1L, HASH).isEmpty());
    }

    @Test
    void verificar_registroRecebido_deveLancarDuplicado() {
        when(repositorio.findByHash(HASH)).thenReturn(Optional.of(registro(1L, "RECEBIDO", LocalDateTime.now().minusDays(3))));

        assertThrows(ArquivoDuplicadoException.class, () -> verificador.verificar(1L, HASH));
    }

    @Test
    void verificar_mesmoHashEmOutroConjunto_deveLancarDuplicado() {
        // A unicidade do hash é global: o mesmo arquivo em outro conjunto também é bloqueado.
        when(repositorio.findByHash(HASH)).thenReturn(Optional.of(registro(2L, "RECEBIDO", LocalDateTime.now().minusDays(3))));

        assertThrows(ArquivoDuplicadoException.class, () -> verificador.verificar(1L, HASH));
    }

    @Test
    void verificar_recebendoDentroDoPrazo_deveLancarDuplicado() {
        when(repositorio.findByHash(HASH)).thenReturn(Optional.of(registro(1L, "RECEBENDO", LocalDateTime.now().minusMinutes(2))));

        assertThrows(ArquivoDuplicadoException.class, () -> verificador.verificar(1L, HASH));
    }

    @Test
    void verificar_recebendoAbandonadoDoMesmoConjunto_deveDevolverParaRetomada() {
        ArquivoBruto abandonado = registro(1L, "RECEBENDO", LocalDateTime.now().minusMinutes(16));
        when(repositorio.findByHash(HASH)).thenReturn(Optional.of(abandonado));

        assertSame(abandonado, verificador.verificar(1L, HASH).orElseThrow());
    }

    @Test
    void verificar_recebendoAbandonadoDeOutroConjunto_deveLancarDuplicado() {
        // Retomar um registro de outro conjunto gravaria o envio no conjunto errado.
        when(repositorio.findByHash(HASH)).thenReturn(Optional.of(registro(2L, "RECEBENDO", LocalDateTime.now().minusHours(2))));

        assertThrows(ArquivoDuplicadoException.class, () -> verificador.verificar(1L, HASH));
    }

    @Test
    void verificar_deveNormalizarOHashAntesDeConsultar() {
        when(repositorio.findByHash(HASH)).thenReturn(Optional.empty());

        verificador.verificar(1L, "  " + HASH.toUpperCase() + "  ");

        verify(repositorio).findByHash(HASH);
    }

    @Test
    void verificar_deveInformarConjuntoEHashNaExcecao() {
        when(repositorio.findByHash(HASH)).thenReturn(Optional.of(registro(1L, "RECEBIDO", LocalDateTime.now())));

        ArquivoDuplicadoException erro = assertThrows(ArquivoDuplicadoException.class, () -> verificador.verificar(1L, HASH));

        assertEquals(1L, erro.getConjuntoId());
        assertEquals(HASH, erro.getHash());
    }

    @Test
    void verificar_argumentosAusentes_deveLancarIllegalArgumentSemConsultar() {
        assertThrows(IllegalArgumentException.class, () -> verificador.verificar(null, HASH));
        assertThrows(IllegalArgumentException.class, () -> verificador.verificar(1L, null));
        assertThrows(IllegalArgumentException.class, () -> verificador.verificar(1L, "  "));

        verifyNoInteractions(repositorio);
    }

    private static ArquivoBruto registro(Long conjuntoId, String status, LocalDateTime dataUpload) {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(conjuntoId);

        ArquivoBruto registro = new ArquivoBruto();
        registro.setId(10L);
        registro.setConjunto(conjunto);
        registro.setHash(HASH);
        registro.setStatus(status);
        registro.setDataUpload(dataUpload);
        return registro;
    }
}