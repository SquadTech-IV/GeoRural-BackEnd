package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.ResultadoIRL;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculoIRLServiceTest {

    @Mock ParametrosRegraService parametrosRegraService;
    @InjectMocks CalculoIRLService service;

    @Test
    void calcular_deveLerMinimoDaRegra_eApurarSaldo() {
        when(parametrosRegraService.daRegraVigente("IRL"))
                .thenReturn(new ParametrosVigentes("IRL", "v1", Map.of("minimo_rl_percent", "20")));

        ResultadoIRL r = service.calcular(new BigDecimal("15"), new BigDecimal("100"));

        assertThat(r.versaoRegra()).isEqualTo("v1");
        assertThat(r.minimoPercent()).isEqualByComparingTo("20");
        assertThat(r.percentualRL()).isEqualByComparingTo("15");
        assertThat(r.areaMinimaHa()).isEqualByComparingTo("20");
        assertThat(r.saldoHa()).isEqualByComparingTo("-5");
    }
}