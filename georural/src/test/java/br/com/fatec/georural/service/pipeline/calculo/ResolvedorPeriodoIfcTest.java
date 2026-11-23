package br.com.fatec.georural.service.pipeline.calculo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolvedorPeriodoIfcTest {

    @Mock JdbcTemplate jdbc;
    @InjectMocks ResolvedorPeriodoIfc resolvedor;

    @Test
    void dataInformada_vencedeTudo() {
        var r = resolvedor.resolver(7L, "2026-10", "2026-03-15", 12);

        assertThat(r.periodo().fim()).isEqualTo(LocalDate.of(2026, 3, 15));
        assertThat(r.origem()).contains("dataReferenciaIfc");
        verifyNoInteractions(jdbc);
    }

    @Test
    void semDataInformada_usaACompetenciaDaRodada() {
        var r = resolvedor.resolver(7L, "2026-10", null, 12);

        assertThat(r.periodo().fim()).isEqualTo(LocalDate.of(2026, 10, 31));
        verifyNoInteractions(jdbc);
    }

    @Test
    void semCompetenciaNaRodada_usaADaVersao() {
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn("2025-08");

        var r = resolvedor.resolver(7L, null, null, 6);

        assertThat(r.periodo().fim()).isEqualTo(LocalDate.of(2025, 8, 31));
        assertThat(r.periodo().inicio()).isEqualTo(LocalDate.of(2025, 3, 1));
        assertThat(r.origem()).contains("versao");
    }

    @Test
    void semNenhumaCompetencia_terminaHoje_semFalhar() {
        when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        var r = resolvedor.resolver(7L, null, null, 12);

        assertThat(r.periodo().fim()).isEqualTo(LocalDate.now());
    }

    @Test
    void dataInformadaInvalida_falhaComMensagemClara() {
        assertThatThrownBy(() -> resolvedor.resolver(7L, null, "15/03/2026", 12))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataReferenciaIfc");
    }
}
