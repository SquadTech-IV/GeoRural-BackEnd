package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.ResultadoIDesmat;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculoIDesmatServiceTest {

    @Mock
    private ParametrosRegraService parametrosRegraService;

    @InjectMocks
    private CalculoIDesmatService service;

    private final GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4674);

    @BeforeEach
    void setUp() {
        when(parametrosRegraService.daRegraVigente("IDESMAT")).thenReturn(
                new ParametrosVigentes("IDESMAT", "v1", Map.of(
                        "marco_2008", "2008-07-22",
                        "marco_2019", "2019-07-31",
                        "marco_2020", "2020-12-31")));
    }

    @Test
    void calcular_imovelSemDesmatamento_deveRetornarZeroEmTodosOsMarcos() {
        ResultadoIDesmat r = service.calcular(List.of(), new BigDecimal("250.0000"));

        assertThat(r.versaoRegra()).isEqualTo("v1");
        assertThat(r.marcos()).hasSize(3);
        assertThat(r.marcos()).allSatisfy(m -> {
            assertThat(m.areaHa()).isEqualByComparingTo("0.0000");
            assertThat(m.percentual()).isEqualByComparingTo("0.0000");
        });
    }

    @Test
    void calcular_desmatamentoAntesEDepoisDeCadaData_deveSomarApenasAposOMarco() {
        List<CalculoIDesmatService.EventoDesmatamento> eventos = List.of(
                // Antes e na data de 22/07/2008 -> nao entram
                new CalculoIDesmatService.EventoDesmatamento(LocalDate.of(2008, 7, 21), new BigDecimal("10.0")),
                new CalculoIDesmatService.EventoDesmatamento(LocalDate.of(2008, 7, 22), new BigDecimal("4.0")),
                // Entre 22/07/2008 e 31/07/2019 -> apenas marco_2008
                new CalculoIDesmatService.EventoDesmatamento(LocalDate.of(2012, 3, 15), new BigDecimal("2.5")),
                // Na data de 31/07/2019 -> apenas marco_2008 (nao e posterior a 2019-07-31)
                new CalculoIDesmatService.EventoDesmatamento(LocalDate.of(2019, 7, 31), new BigDecimal("1.5")),
                // Entre 31/07/2019 e 31/12/2020 -> marco_2008 e marco_2019
                new CalculoIDesmatService.EventoDesmatamento(LocalDate.of(2020, 6, 10), new BigDecimal("3.0")),
                // Apos 31/12/2020 -> marco_2008, marco_2019 e marco_2020
                new CalculoIDesmatService.EventoDesmatamento(LocalDate.of(2022, 1, 5), new BigDecimal("2.0"))
        );

        ResultadoIDesmat r = service.calcular(eventos, new BigDecimal("100.0000"));

        // marco_2008: 2.5 + 1.5 + 3.0 + 2.0 = 9.0 ha (9.0000%)
        assertThat(r.marcos().get(0).marco()).isEqualTo("marco_2008");
        assertThat(r.marcos().get(0).data()).isEqualTo(LocalDate.of(2008, 7, 22));
        assertThat(r.marcos().get(0).areaHa()).isEqualByComparingTo("9.0000");
        assertThat(r.marcos().get(0).percentual()).isEqualByComparingTo("9.0000");

        // marco_2019: 3.0 + 2.0 = 5.0 ha (5.0000%)
        assertThat(r.marcos().get(1).marco()).isEqualTo("marco_2019");
        assertThat(r.marcos().get(1).data()).isEqualTo(LocalDate.of(2019, 7, 31));
        assertThat(r.marcos().get(1).areaHa()).isEqualByComparingTo("5.0000");
        assertThat(r.marcos().get(1).percentual()).isEqualByComparingTo("5.0000");

        // marco_2020: 2.0 ha (2.0000%)
        assertThat(r.marcos().get(2).marco()).isEqualTo("marco_2020");
        assertThat(r.marcos().get(2).data()).isEqualTo(LocalDate.of(2020, 12, 31));
        assertThat(r.marcos().get(2).areaHa()).isEqualByComparingTo("2.0000");
        assertThat(r.marcos().get(2).percentual()).isEqualByComparingTo("2.0000");
    }

    @Test
    void calcular_intersecaoParcialEPoligonosSobrepostosDissolvidos_naoDevePassarDaAreaDoImovel() {
        // Imovel 10x10 (100 ha equivalentes)
        Geometry imovel = criarRetangulo(0, 0, 10, 10);
        // Poligono 1 com intersecao parcial fora do imovel (-4..6)
        Geometry p1 = criarRetangulo(-4, 0, 6, 10);
        // Poligono 2 sobrepondo p1 (2..9) dentro do imovel
        Geometry p2 = criarRetangulo(2, 0, 9, 10);

        // Uniao dissolvida dentro do imovel = [0..9] x [0..10] -> 90% da area do imovel
        Geometry dissolvidaNoImovel = p1.union(p2).intersection(imovel);
        double proporcao = dissolvidaNoImovel.getArea() / imovel.getArea();
        BigDecimal areaHaDissolvida = BigDecimal.valueOf(100.0 * proporcao);

        ResultadoIDesmat r = service.calcular(
                List.of(new CalculoIDesmatService.EventoDesmatamento(
                        LocalDate.of(2023, 4, 1), areaHaDissolvida)),
                new BigDecimal("100.0000"));

        assertThat(r.marcos().get(2).areaHa()).isEqualByComparingTo("90.0000");
        assertThat(r.marcos().get(2).percentual()).isEqualByComparingTo("90.0000");
        assertThat(r.marcos().get(2).percentual()).isLessThanOrEqualTo(new BigDecimal("100.0000"));
    }

    private Geometry criarRetangulo(double x1, double y1, double x2, double y2) {
        return gf.createPolygon(new Coordinate[]{
                new Coordinate(x1, y1),
                new Coordinate(x2, y1),
                new Coordinate(x2, y2),
                new Coordinate(x1, y2),
                new Coordinate(x1, y1)
        });
    }
}