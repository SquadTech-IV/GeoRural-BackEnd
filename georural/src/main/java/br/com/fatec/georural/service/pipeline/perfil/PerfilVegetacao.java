package br.com.fatec.georural.service.pipeline.perfil;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PerfilVegetacao implements PerfilCamada {

    @Override
    public String tipoConjunto() {
        return "VEGETACAO";
    }

    @Override
    public String rotulo() {
        return "Cobertura vegetal nativa";
    }

    @Override
    public List<CampoCamada> campos() {
        return List.of(
                // 'tema' e o nome do campo no shapefile do SICAR
                CampoCamada.opcional("classe", TipoCampo.TEXTO,
                        "tema", "cod_classe", "des_classe", "tipo", "ind_tipo", "classe_cob"),
                CampoCamada.opcional("area_declarada", TipoCampo.DECIMAL,
                        "area", "num_area", "area_ha", "num_area_h"),
                CampoCamada.opcional("codigo_car", TipoCampo.TEXTO,
                        "recibo", "cod_imovel", "codigo", "cod_car", "car"));
    }

    @Override
    public int ordemNoTratamento() {
        return 25;
    }

    @Override
    public List<String> nomesDeCamada() {
        return List.of("COBERTURA_DO_SOLO", "COBERTURA_SOLO", "COBERTURA_VEGETAL",
                "VEGETACAO", "VEGETACAO_NATIVA", "REMANESCENTE_VEGETACAO_NATIVA");
    }

    @Override
    public String campoIdentificador() {
        return null;
    }

    @Override
    public TipoGeometria geometriaEsperada() {
        return TipoGeometria.POLIGONO;
    }
}