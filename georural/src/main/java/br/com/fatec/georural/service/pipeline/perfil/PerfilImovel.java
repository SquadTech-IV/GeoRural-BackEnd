package br.com.fatec.georural.service.pipeline.perfil;

import org.springframework.stereotype.Component;

import java.util.List;

// imovel rural do CAR (camada AREA_IMOVEL do SICAR)
@Component
public class PerfilImovel implements PerfilCamada {

    @Override
    public String tipoConjunto() {
        return "IMOVEL";
    }

    @Override
    public String rotulo() {
        return "Imovel rural (CAR)";
    }

    @Override
    public List<CampoCamada> campos() {
        return List.of(
                CampoCamada.obrigatorio("codigo_car", TipoCampo.TEXTO,
                        "cod_imovel", "codigo", "cod_car", "car", "codigoimovel"),
                CampoCamada.opcional("nome", TipoCampo.TEXTO,
                        "nome_imovel", "nom_imovel", "denominacao", "nome_area", "nom_area",
                        "des_imovel", "nome_propriedade"),
                CampoCamada.opcional("area_declarada", TipoCampo.DECIMAL,
                        "num_area", "area", "area_ha", "num_area_h", "areaimovel"),
                CampoCamada.opcional("municipio_nome", TipoCampo.TEXTO,
                        "municipio", "nom_munici", "nm_mun", "nome_munic"),
                CampoCamada.opcional("municipio_codigo", TipoCampo.TEXTO,
                        "cod_munici", "cd_mun", "geocodigo", "cod_ibge"),
                CampoCamada.opcional("uf", TipoCampo.TEXTO,
                        "cod_estado", "estado", "sigla_uf"),
                CampoCamada.opcional("situacao", TipoCampo.TEXTO,
                        "ind_status", "status", "des_condic"),
                CampoCamada.opcional("tipo", TipoCampo.TEXTO,
                        "ind_tipo", "tipo_imovel"),
                CampoCamada.opcional("data_informada", TipoCampo.DATA,
                        "dat_criaca", "data_criacao", "dat_cadastr"));
    }

    @Override
    public int ordemNoTratamento() {
        return 20;
    }

    @Override
    public List<String> nomesDeCamada() {
        return List.of("AREA_IMOVEL", "IMOVEL", "AREA_DO_IMOVEL", "PERIMETRO_IMOVEL");
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
