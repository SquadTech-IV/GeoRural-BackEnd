package br.com.fatec.georural.service.pipeline.perfil;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PerfilVegetacao implements PerfilCamada {

    @Override
    public String tipoConjunto() {
        return "VEGETACAO_NATIVA";
    }

    @Override
    public String rotulo() {
        return "Vegetação Nativa";
    }

    @Override
    public List<CampoCamada> campos() {
        return List.of(
                CampoCamada.obrigatorio("codigo_car", TipoCampo.TEXTO,
                        "cod_imovel", "codigo", "cod_car", "car", "codigoimovel"),
                CampoCamada.opcional("area_declarada", TipoCampo.DECIMAL,
                        "num_area", "area", "area_ha", "num_area_h", "areaha"),
                CampoCamada.opcional("tipo", TipoCampo.TEXTO,
                        "ind_tipo", "tipo_veg", "des_tipo", "categoria"),
                CampoCamada.opcional("situacao", TipoCampo.TEXTO,
                        "ind_status", "status", "des_condic")
        );
    }

    @Override
    public int ordemNoTratamento() {
        // Define a ordem de execução no pipeline
        return 50;
    }

    @Override
    public List<String> nomesDeCamada() {
        return List.of("VEGETACAO_NATIVA", "REMANESCENTE_VEGETACAO", "COBERTURA_VEGETAL", "SUPRESSAO_VEGETACAO", "AREA_VEGETACAO");
    }

    @Override
    public String campoIdentificador() {
        return "codigo_car";
    }

    @Override
    public TipoGeometria geometriaEsperada() {
        return TipoGeometria.POLIGONO;
    }
}