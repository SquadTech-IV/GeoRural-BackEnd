package br.com.fatec.georural.service.pipeline.perfil;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PerfilDesmatamento implements PerfilCamada {

    @Override
    public String tipoConjunto() {
        return "DESMATAMENTO";
    }

    @Override
    public String rotulo() {
        return "Desmatamento (PRODES)";
    }

    @Override
    public List<CampoCamada> campos() {
        return List.of(
                CampoCamada.opcional("identificador", TipoCampo.TEXTO,
                        "uid", "gid", "objectid", "id", "cod_poligono"),
                CampoCamada.opcional("data_corte", TipoCampo.DATA,
                        "view_date", "data_deteccao", "data_corte", "data", "img_date", "julday"),
                CampoCamada.opcional("ano", TipoCampo.INTEIRO,
                        "year", "ano", "ano_ref", "ano_prodes", "nu_ano"),
                CampoCamada.opcional("bioma", TipoCampo.TEXTO,
                        "bioma", "nm_bioma", "biome"),
                CampoCamada.opcional("classe", TipoCampo.TEXTO,
                        "class_name", "classe", "classname", "subclasse")
        );
    }

    @Override
    public String campoIdentificador() {
        return "identificador";
    }

    @Override
    public TipoGeometria geometriaEsperada() {
        return TipoGeometria.POLIGONO;
    }

    @Override
    public int ordemNoTratamento() {
        return 35;
    }

    @Override
    public List<String> nomesDeCamada() {
        return List.of("DESMATAMENTO", "PRODES", "PRODES_MATA_ATLANTICA", "SUPRESSAO");
    }
}