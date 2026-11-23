package br.com.fatec.georural.service.pipeline.perfil;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

// focos de calor detectados por satelite (INPE Queimadas, NASA FIRMS): um ponto e uma data
// por linha. Alimenta o IFC (US#26)
@Component
public class PerfilFocoCalor implements PerfilCamada {

    public static final String CAMPO_DATA = "data_foco";

    @Override
    public String tipoConjunto() {
        return "FOCO_CALOR";
    }

    @Override
    public String rotulo() {
        return "Focos de calor";
    }

    @Override
    public List<CampoCamada> campos() {
        return List.of(
                // sem data o foco nao entra em periodo nenhum, entao ela e obrigatoria.
                // 'datahora' e do INPE, 'acq_date' e do FIRMS
                CampoCamada.obrigatorio(CAMPO_DATA, TipoCampo.DATA,
                        "data", "datahora", "data_hora", "acq_date", "dt_foco", "dat_foco",
                        "data_pas", "data_deteccao"),
                CampoCamada.opcional("satelite", TipoCampo.TEXTO,
                        "satellite", "sat", "nome_satelite"),
                CampoCamada.opcional("municipio_nome", TipoCampo.TEXTO,
                        "municipio", "nm_municip", "nm_mun", "county"));
    }

    // o ponto vem das colunas de latitude/longitude, lidas pelo LeitorCsv, ou da propria
    // geometria do shapefile; nao existe campo identificador no arquivo
    @Override
    public String campoIdentificador() {
        return null;
    }

    // data|latitude|longitude: e o que torna dois focos "o mesmo foco". Sem isso o hash
    // da versao so enxergaria a quantidade de linhas e um arquivo novo com o mesmo numero
    // de focos passaria por "conteudo identico"
    @Override
    public String identificadorDe(Map<String, Object> campos, Geometry geometria,
                                  String identificadorDoCampo) {
        Object data = campos.get(CAMPO_DATA);
        if (data == null || geometria == null || geometria.isEmpty()) {
            return identificadorDoCampo;
        }
        Coordinate posicao = geometria.getCoordinate();
        return String.format(Locale.ROOT, "%s|%.6f|%.6f", data, posicao.y, posicao.x);
    }

    @Override
    public TipoGeometria geometriaEsperada() {
        return TipoGeometria.PONTO;
    }

    // depois dos imoveis: o ponto so serve para contar dentro deles
    @Override
    public int ordemNoTratamento() {
        return 60;
    }

    @Override
    public List<String> nomesDeCamada() {
        return List.of("FOCO_CALOR", "FOCOS_CALOR", "FOCOS", "FOCOS_QUEIMADAS", "QUEIMADAS");
    }
}
