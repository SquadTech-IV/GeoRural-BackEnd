package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.ResultadoIFC;
import br.com.fatec.georural.service.CalculoIFCService.FocoDetectado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CalculoIFCServiceTest {

    private static final GeometryFactory FABRICA = new GeometryFactory(new PrecisionModel(), 4674);
    private static final LocalDate REF = LocalDate.of(2026, 1, 1);

    @Mock ParametrosRegraService parametrosRegraService;
    @InjectMocks CalculoIFCService service;

    // imovel quadrado de 0,01 grau de lado, com um vizinho encostado na divisa x = -50,99
    private Geometry imovel;
    private Geometry vizinho;

    @BeforeEach
    void preparar() {
        lenient().when(parametrosRegraService.daRegraVigente("IFC")).thenReturn(
                new ParametrosVigentes("IFC", "v1", Map.of(
                        "fator_area", "1000",
                        "periodo_meses", "12")));
        imovel = quadrado(-51.00, -25.00, -50.99, -24.99);
        vizinho = quadrado(-50.99, -25.00, -50.98, -24.99);
    }

    @Test
    void calcular_deveContarFocosNoPeriodo_eNormalizarPelaArea() {
        List<LocalDate> focos = List.of(
                REF.minusMonths(1),   // dentro da janela de 12 meses
                REF.minusMonths(6),   // dentro
                REF.minusMonths(11),  // dentro
                REF.minusMonths(20)); // FORA

        ResultadoIFC r = service.calcular(focos, new BigDecimal("500"), REF);

        assertThat(r.versaoRegra()).isEqualTo("v1");
        assertThat(r.periodoMeses()).isEqualTo(12);
        assertThat(r.fator()).isEqualByComparingTo("1000");
        assertThat(r.focosNoPeriodo()).isEqualTo(3L);
        assertThat(r.indice()).isEqualByComparingTo("6");
    }

    // ---- imovel sem focos ----

    @Test
    void imovelSemFocos_temIndiceZero_enaoNulo() {
        ResultadoIFC r = service.calcularNoImovel(imovel, List.of(), new BigDecimal("800"), REF);

        assertThat(r.focosNoPeriodo()).isZero();
        assertThat(r.indice()).isEqualByComparingTo("0");
    }

    @Test
    void focoForaDoImovel_naoContaNele() {
        FocoDetectado longe = new FocoDetectado(REF.minusDays(10), ponto(-50.50, -24.50));

        ResultadoIFC r = service.calcularNoImovel(imovel, List.of(longe), new BigDecimal("800"), REF);

        assertThat(r.focosNoPeriodo()).isZero();
        assertThat(r.indice()).isEqualByComparingTo("0");
    }

    @Test
    void imovelSemArea_naoTemIndice() {
        FocoDetectado dentro = new FocoDetectado(REF.minusDays(10), ponto(-50.995, -24.995));

        ResultadoIFC zero = service.calcularNoImovel(imovel, List.of(dentro), BigDecimal.ZERO, REF);
        ResultadoIFC nulo = service.calcularNoImovel(imovel, List.of(dentro), null, REF);

        assertThat(zero.focosNoPeriodo()).isEqualTo(1L);
        assertThat(zero.indice()).isNull();
        assertThat(nulo.indice()).isNull();
    }

    // ---- ponto na divisa ----

    @Test
    void focoSobreADivisa_contaComoDentro() {
        FocoDetectado naBorda = new FocoDetectado(REF.minusDays(5), ponto(-51.00, -24.995));
        FocoDetectado noVertice = new FocoDetectado(REF.minusDays(5), ponto(-51.00, -25.00));

        ResultadoIFC r = service.calcularNoImovel(imovel, List.of(naBorda, noVertice),
                new BigDecimal("1000"), REF);

        assertThat(r.focosNoPeriodo()).isEqualTo(2L);
        assertThat(r.indice()).isEqualByComparingTo("2");
    }

    @Test
    void focoNaDivisaEntreDoisImoveis_contaNosDois() {
        FocoDetectado naDivisa = new FocoDetectado(REF.minusDays(5), ponto(-50.99, -24.995));

        ResultadoIFC a = service.calcularNoImovel(imovel, List.of(naDivisa), new BigDecimal("500"), REF);
        ResultadoIFC b = service.calcularNoImovel(vizinho, List.of(naDivisa), new BigDecimal("500"), REF);

        assertThat(a.focosNoPeriodo()).isEqualTo(1L);
        assertThat(b.focosNoPeriodo()).isEqualTo(1L);
    }

    // ---- periodo sem dados ----

    @Test
    void periodoSemDados_todosOsFocosForaDaJanela_dazeroFocos() {
        // todos anteriores ao inicio da janela (02/01/2025) ou posteriores ao fim
        List<LocalDate> foraDaJanela = List.of(
                REF.minusMonths(12),      // 01/01/2025: um dia antes do inicio
                REF.minusYears(3),
                REF.plusDays(1));         // depois da data de referencia

        ResultadoIFC r = service.calcular(foraDaJanela, new BigDecimal("500"), REF);

        assertThat(r.focosNoPeriodo()).isZero();
        assertThat(r.indice()).isEqualByComparingTo("0");
    }

    @Test
    void listaDeFocosVazia_dazero() {
        ResultadoIFC r = service.calcular(List.of(), new BigDecimal("500"), REF);

        assertThat(r.focosNoPeriodo()).isZero();
        assertThat(r.indice()).isEqualByComparingTo("0");
    }

    @Test
    void extremosDaJanelaContam_inicioEFim() {
        LocalDate inicio = REF.plusDays(1).minusMonths(12); // 02/01/2025
        ResultadoIFC r = service.calcular(List.of(inicio, REF), new BigDecimal("1000"), REF);

        assertThat(r.focosNoPeriodo()).isEqualTo(2L);
    }

    @Test
    void indice_arredondaEmQuatroCasas() {
        assertThat(CalculoIFCService.indice(1, new BigDecimal("3"), new BigDecimal("1000")))
                .isEqualByComparingTo("333.3333");
        // 1 foco em 0,5 ha: 2.000 focos/1.000 ha; passa de 999,9999, por isso a V9 amplia a coluna
        assertThat(CalculoIFCService.indice(1, new BigDecimal("0.5"), new BigDecimal("1000")))
                .isEqualByComparingTo("2000");
    }

    private static Geometry quadrado(double x1, double y1, double x2, double y2) {
        return FABRICA.createPolygon(new Coordinate[] {
                new Coordinate(x1, y1), new Coordinate(x2, y1), new Coordinate(x2, y2),
                new Coordinate(x1, y2), new Coordinate(x1, y1)});
    }

    private static Geometry ponto(double x, double y) {
        return FABRICA.createPoint(new Coordinate(x, y));
    }
}
