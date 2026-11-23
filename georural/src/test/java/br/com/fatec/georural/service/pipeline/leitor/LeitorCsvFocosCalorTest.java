package br.com.fatec.georural.service.pipeline.leitor;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// US#26.01: CSV de focos (latitude/longitude) vira ponto em EPSG:4674
class LeitorCsvFocosCalorTest {

    private final LeitorCsv leitor = new LeitorCsv(null);

    private List<FeicaoLida> ler(String csv, Integer srs) {
        ContextoLeitura ctx = new ContextoLeitura(FormatoArquivo.CSV, "focos_2026-10.csv", null,
                () -> new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), srs);
        List<FeicaoLida> lidas = new ArrayList<>();
        leitor.ler(ctx, lidas::add);
        return lidas;
    }

    @Test
    void csvDoInpe_comVirgula_geraPontos() {
        String csv = """
                datahora,satelite,municipio,latitude,longitude
                2026-10-05 17:32:00,AQUA_M-T,CURITIBA,-25.4284,-49.2733
                2026-10-06 03:10:00,NOAA-20,LONDRINA,-23.31,-51.16
                """;

        List<FeicaoLida> focos = ler(csv, null);

        assertThat(focos).hasSize(2);
        Point primeiro = (Point) focos.get(0).geometria();
        assertThat(primeiro.getX()).isEqualTo(-49.2733);
        assertThat(primeiro.getY()).isEqualTo(-25.4284);
        assertThat(primeiro.getSRID()).isEqualTo(4674);
        assertThat(focos.get(0).atributoTexto("datahora")).isEqualTo("2026-10-05 17:32:00");
    }

    @Test
    void csvComPontoEVirgula_edecimalComVirgula_geraPontos() {
        String csv = "data;latitude;longitude\n05/10/2026;\"-25,4284\";\"-49,2733\"\n";

        List<FeicaoLida> focos = ler(csv, 4326);

        assertThat(focos).hasSize(1);
        Point p = (Point) focos.get(0).geometria();
        assertThat(p.getY()).isEqualTo(-25.4284);
        assertThat(p.getSRID()).isEqualTo(4326); // a reprojecao para 4674 e do padronizador
    }

    @Test
    void linhaSemCoordenada_vemSemGeometria_paraOPadronizadorRejeitar() {
        String csv = "datahora,latitude,longitude\n2026-10-05,,\n";

        List<FeicaoLida> focos = ler(csv, null);

        assertThat(focos).hasSize(1);
        assertThat(focos.get(0).temGeometria()).isFalse();
    }
}
