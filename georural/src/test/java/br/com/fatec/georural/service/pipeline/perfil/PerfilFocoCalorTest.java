package br.com.fatec.georural.service.pipeline.perfil;

import br.com.fatec.georural.service.pipeline.leitor.FeicaoLida;
import br.com.fatec.georural.service.pipeline.validacao.FeicaoPadronizada;
import br.com.fatec.georural.service.pipeline.validacao.PadronizadorFeicao;
import br.com.fatec.georural.service.pipeline.validacao.ReprojetorFeicao;
import br.com.fatec.georural.service.pipeline.validacao.RegrasQualidade;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PerfilFocoCalorTest {

    private static final GeometryFactory FABRICA = new GeometryFactory(new PrecisionModel(), 4674);

    private final PerfilFocoCalor perfil = new PerfilFocoCalor();
    private final PadronizadorFeicao padronizador = new PadronizadorFeicao(new ReprojetorFeicao());

    @Test
    void perfilEntraNoRegistro_epelosNomesDeCamadaEsperados() {
        RegistroPerfis registro = new RegistroPerfis(List.of(perfil));

        assertThat(registro.buscar("FOCO_CALOR")).containsSame(perfil);
        assertThat(registro.porNomeDeCamada("focos_2026-10.csv")).containsSame(perfil);
        assertThat(registro.porNomeDeCamada("QUEIMADAS")).containsSame(perfil);
    }

    @Test
    void focoDoInpe_viraFeicaoComDataEIdentificadorComposto() {
        FeicaoLida inpe = linha(1, -25.4284, -49.2733,
                "datahora", "2026-10-05 17:32:00", "satelite", "AQUA_M-T", "municipio", "CURITIBA");

        FeicaoPadronizada f = padronizador.padronizar(perfil, inpe, 4674);

        assertThat(f.rejeitada()).isFalse();
        assertThat(f.atributos().get("data_foco")).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(f.atributos().get("satelite")).isEqualTo("AQUA_M-T");
        assertThat(f.identificador()).isEqualTo("2026-10-05|-25.428400|-49.273300");
        assertThat(f.geometria().getSRID()).isEqualTo(4674);
    }

    @Test
    void focoDoFirms_comAcqDate_eAceito() {
        FeicaoLida firms = linha(1, -23.5, -51.2, "acq_date", "2026-09-30", "satellite", "N");

        FeicaoPadronizada f = padronizador.padronizar(perfil, firms, 4674);

        assertThat(f.rejeitada()).isFalse();
        assertThat(f.atributos().get("data_foco")).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void focosDeDatasDiferentes_naMesmaPosicao_temIdentificadoresDiferentes() {
        FeicaoPadronizada a = padronizador.padronizar(perfil,
                linha(1, -25.1, -50.1, "datahora", "2026-10-05"), 4674);
        FeicaoPadronizada b = padronizador.padronizar(perfil,
                linha(2, -25.1, -50.1, "datahora", "2026-10-06"), 4674);

        assertThat(a.identificador()).isNotEqualTo(b.identificador());
    }

    @Test
    void semData_eRejeitado() {
        FeicaoPadronizada f = padronizador.padronizar(perfil,
                linha(1, -25.1, -50.1, "satelite", "AQUA_M-T"), 4674);

        assertThat(f.rejeitada()).isTrue();
        assertThat(f.erros()).anyMatch(p -> p.regra().equals(RegrasQualidade.CAMPO_OBRIGATORIO_AUSENTE)
                && p.campo().equals("data_foco"));
        assertThat(f.identificador()).isNull();
    }

    @Test
    void dataIlegivel_eRejeitada() {
        FeicaoPadronizada f = padronizador.padronizar(perfil,
                linha(1, -25.1, -50.1, "datahora", "ontem"), 4674);

        assertThat(f.erros()).anyMatch(p -> p.regra().equals(RegrasQualidade.TIPO_INVALIDO));
    }

    @Test
    void semCoordenadas_eRejeitado() {
        FeicaoLida semPonto = new FeicaoLida(1, mapa("datahora", "2026-10-05"), null);

        FeicaoPadronizada f = padronizador.padronizar(perfil, semPonto, 4674);

        assertThat(f.rejeitada()).isTrue();
        assertThat(f.erros()).anyMatch(p -> p.regra().equals(RegrasQualidade.GEOMETRIA_AUSENTE));
    }

    @Test
    void focoForaDoParana_eRejeitadoNaValidacao() {
        // Rio de Janeiro: fora da faixa de longitude -55 a -48
        FeicaoPadronizada f = padronizador.padronizar(perfil,
                linha(1, -22.9, -43.2, "datahora", "2026-10-05"), 4674);

        assertThat(f.rejeitada()).isTrue();
        assertThat(f.erros()).anyMatch(
                p -> p.regra().equals(RegrasQualidade.FORA_DA_AREA_DE_INTERESSE));
    }

    @Test
    void geometriaQueNaoEPonto_eRejeitada() {
        var poligono = FABRICA.createPolygon(new Coordinate[] {
                new Coordinate(-50, -25), new Coordinate(-49, -25),
                new Coordinate(-49, -24), new Coordinate(-50, -25)});
        FeicaoLida f = new FeicaoLida(1, mapa("datahora", "2026-10-05"), poligono);

        assertThat(padronizador.padronizar(perfil, f, 4674).erros())
                .anyMatch(p -> p.regra().equals(RegrasQualidade.GEOMETRIA_INVALIDA));
    }

    private static FeicaoLida linha(long numero, double lat, double lon, String... pares) {
        return new FeicaoLida(numero, mapa(pares), FABRICA.createPoint(new Coordinate(lon, lat)));
    }

    private static Map<String, Object> mapa(String... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return m;
    }
}
