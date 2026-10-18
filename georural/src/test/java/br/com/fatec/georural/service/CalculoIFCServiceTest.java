package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.ResultadoIFC;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculoIFCServiceTest {

    @Mock ParametrosRegraService parametrosRegraService;
    @InjectMocks CalculoIFCService service;

    @Test
    void calcular_deveContarFocosNoPeriodo_eNormalizarPelaArea() {
        when(parametrosRegraService.daRegraVigente("IFC")).thenReturn(
                new ParametrosVigentes("IFC", "v1", Map.of(
                        "fator_area", "1000",
                        "periodo_meses", "12")));

        LocalDate ref = LocalDate.of(2026, 1, 1);
        List<LocalDate> focos = List.of(
                ref.minusMonths(1),   // dentro da janela de 12 meses
                ref.minusMonths(6),   // dentro
                ref.minusMonths(11),  // dentro
                ref.minusMonths(20)); // FORA

        ResultadoIFC r = service.calcular(focos, new BigDecimal("500"), ref);

        assertThat(r.versaoRegra()).isEqualTo("v1");
        assertThat(r.periodoMeses()).isEqualTo(12);
        assertThat(r.fator()).isEqualByComparingTo("1000");
        assertThat(r.focosNoPeriodo()).isEqualTo(3L);
        assertThat(r.indice()).isEqualByComparingTo("6");
    }
}