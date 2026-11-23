package br.com.fatec.georural.service.comparacao;

import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.SituacaoImovel;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Valores;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ComparacaoVersaoServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ComparacaoVersaoService service = new ComparacaoVersaoService(jdbc);

    private static Valores v(String pct, String ha, String sit) {
        return new Valores(pct == null ? null : new BigDecimal(pct), ha == null ? null : new BigDecimal(ha),
                null, sit);
    }

    @Test
    @DisplayName("Duas versoes iguais sao rejeitadas antes de consultar o banco")
    void mesmaVersao() {
        assertThrows(RequisicaoInvalidaException.class,
                () -> service.comparar("IAE", 1L, 1L, false, 0, 20));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("Parametros obrigatorios e paginacao invalida sao rejeitados")
    void parametrosInvalidos() {
        assertThrows(RequisicaoInvalidaException.class, () -> service.comparar(" ", 1L, 2L, false, 0, 20));
        assertThrows(RequisicaoInvalidaException.class, () -> service.comparar("IAE", null, 2L, false, 0, 20));
        assertThrows(RequisicaoInvalidaException.class, () -> service.comparar("IAE", 1L, 2L, false, -1, 20));
        assertThrows(RequisicaoInvalidaException.class, () -> service.comparar("IAE", 1L, 2L, false, 0, 500));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("Situacao do imovel: so em B, so em A, igual (escala diferente) e alterado")
    void situacaoDoImovel() {
        assertEquals(SituacaoImovel.APENAS_EM_B, ComparacaoVersaoService.situacao(null, v("1", "1", "OK")));
        assertEquals(SituacaoImovel.APENAS_EM_A, ComparacaoVersaoService.situacao(v("1", "1", "OK"), null));
        assertEquals(SituacaoImovel.IGUAL,
                ComparacaoVersaoService.situacao(v("10.0", "5", "OK"), v("10.0000", "5.00", "OK")));
        assertEquals(SituacaoImovel.ALTERADO,
                ComparacaoVersaoService.situacao(v("10", "5", "OK"), v("12", "5", "OK")));
        assertEquals(SituacaoImovel.ALTERADO,
                ComparacaoVersaoService.situacao(v("10", "5", "OK"), v("10", "5", "CRITICO")));
    }

    @Test
    @DisplayName("Diferenca e B - A e fica nula quando falta um dos lados")
    void diferenca() {
        assertEquals(0, new BigDecimal("2.5").compareTo(
                ComparacaoVersaoService.subtrair(new BigDecimal("12.5"), new BigDecimal("10"))));
        assertNull(ComparacaoVersaoService.subtrair(null, BigDecimal.ONE));
        assertNull(ComparacaoVersaoService.subtrair(BigDecimal.ONE, null));
    }
}
