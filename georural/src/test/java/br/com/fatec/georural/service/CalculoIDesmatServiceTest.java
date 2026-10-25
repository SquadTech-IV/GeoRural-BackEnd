package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.ResultadoIDesmat;
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
class CalculoIDesmatServiceTest {

    @Mock ParametrosRegraService parametrosRegraService;
    @InjectMocks CalculoIDesmatService service;

    @Test
    void calcular_deveSomarSupressaoPorMarco_naOrdemDasDatas() {
        when(parametrosRegraService.daRegraVigente("IDESMAT")).thenReturn(
                new ParametrosVigentes("IDESMAT", "v1", Map.of(
                        "marco_2008", "2008-07-22",
                        "marco_2019", "2019-07-31",
                        "marco_2020", "2020-12-31")));

        List<CalculoIDesmatService.EventoDesmatamento> eventos = List.of(
                new CalculoIDesmatService.EventoDesmatamento(LocalDate.of(2010, 5, 10), new BigDecimal("2.5")),
                new CalculoIDesmatService.EventoDesmatamento(LocalDate.of(2019, 8, 15), new BigDecimal("1.0")),
                new CalculoIDesmatService.EventoDesmatamento(LocalDate.of(2021, 3, 20), new BigDecimal("0.5")));


        ResultadoIDesmat r = service.calcular(eventos, new BigDecimal("100"));

        assertThat(r.versaoRegra()).isEqualTo("v1");
        assertThat(r.marcos()).hasSize(3);
        assertThat(r.marcos().get(0).marco()).isEqualTo("marco_2008");
        assertThat(r.marcos().get(0).areaHa()).isEqualByComparingTo("4.0");   // tudo apos 2008
        assertThat(r.marcos().get(0).percentual()).isEqualByComparingTo("4");
        assertThat(r.marcos().get(1).areaHa()).isEqualByComparingTo("1.5");   // apos 2019
        assertThat(r.marcos().get(2).areaHa()).isEqualByComparingTo("0.5");   // apos 2020
    }
}