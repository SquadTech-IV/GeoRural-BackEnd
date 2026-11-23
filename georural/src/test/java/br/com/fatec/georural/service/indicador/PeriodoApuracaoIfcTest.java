package br.com.fatec.georural.service.indicador;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodoApuracaoIfcTest {

    @Test
    void competenciaMensal_terminaNoUltimoDiaDoMes_ecobreDozeMeses() {
        PeriodoApuracaoIfc p = PeriodoApuracaoIfc.daCompetencia("2026-10", 12);

        assertThat(p.inicio()).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(p.fim()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void competenciaAnual_terminaEm31DeDezembro() {
        PeriodoApuracaoIfc p = PeriodoApuracaoIfc.daCompetencia("2025", 12);

        assertThat(p.inicio()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(p.fim()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void fimDeMesCurto_naoDesalinhaOInicio() {
        assertThat(PeriodoApuracaoIfc.terminandoEm(LocalDate.of(2026, 4, 30), 1).inicio())
                .isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(PeriodoApuracaoIfc.terminandoEm(LocalDate.of(2026, 2, 28), 12).inicio())
                .isEqualTo(LocalDate.of(2025, 3, 1));
    }

    @Test
    void contem_inclusivoNosDoisExtremos() {
        PeriodoApuracaoIfc p = PeriodoApuracaoIfc.daCompetencia("2026-10", 12);

        assertThat(p.contem(LocalDate.of(2025, 11, 1))).isTrue();
        assertThat(p.contem(LocalDate.of(2026, 10, 31))).isTrue();
        assertThat(p.contem(LocalDate.of(2025, 10, 31))).isFalse();
        assertThat(p.contem(LocalDate.of(2026, 11, 1))).isFalse();
        assertThat(p.contem(null)).isFalse();
    }

    @Test
    void periodosConsecutivos_naoContamOMesmoDiaDuasVezes() {
        PeriodoApuracaoIfc anterior = PeriodoApuracaoIfc.daCompetencia("2026-09", 1);
        PeriodoApuracaoIfc seguinte = PeriodoApuracaoIfc.daCompetencia("2026-10", 1);
        LocalDate divisa = LocalDate.of(2026, 9, 30);

        assertThat(anterior.contem(divisa) && seguinte.contem(divisa)).isFalse();
        assertThat(anterior.contem(divisa) || seguinte.contem(divisa)).isTrue();
    }

    @Test
    void entradasInvalidas_falhamComMensagemClara() {
        assertThatThrownBy(() -> PeriodoApuracaoIfc.daCompetencia("outubro", 12))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("outubro");
        assertThatThrownBy(() -> PeriodoApuracaoIfc.daCompetencia(" ", 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PeriodoApuracaoIfc.terminandoEm(LocalDate.now(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PeriodoApuracaoIfc.terminandoEm(null, 12))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
