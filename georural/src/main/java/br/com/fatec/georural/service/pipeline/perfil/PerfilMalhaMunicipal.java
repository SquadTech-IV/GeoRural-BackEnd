package br.com.fatec.georural.service.pipeline.perfil;

import org.springframework.stereotype.Component;

import java.util.List;

// malha municipal do IBGE; e dela que sai o limite do Parana
@Component
public class PerfilMalhaMunicipal implements PerfilCamada {

    @Override
    public String tipoConjunto() {
        return "MALHA_MUNICIPAL";
    }

    @Override
    public String rotulo() {
        return "Malha municipal (IBGE)";
    }

    @Override
    public List<CampoCamada> campos() {
        return List.of(
                CampoCamada.obrigatorio("municipio_codigo", TipoCampo.TEXTO,
                        "cd_mun", "cd_geocmu", "geocodigo", "cod_ibge", "cd_municip"),
                CampoCamada.obrigatorio("municipio_nome", TipoCampo.TEXTO,
                        "nm_mun", "nm_municip", "municipio", "nome"),
                CampoCamada.opcional("uf", TipoCampo.TEXTO,
                        "sigla_uf", "uf_sigla", "nm_uf", "estado"),
                CampoCamada.opcional("area_km2", TipoCampo.DECIMAL,
                        "area_km2", "ar_mun_200", "area"));
    }

    @Override
    public int ordemNoTratamento() {
        return 10;
    }

    @Override
    public List<String> nomesDeCamada() {
        return List.of("MUNICIPIO", "MUNICIPIOS", "MALHA_MUNICIPAL", "BR_MUNICIPIOS",
                "PR_MUNICIPIOS", "LIM_MUNICIPIO");
    }

    @Override
    public String campoIdentificador() {
        return "municipio_codigo";
    }

    @Override
    public TipoGeometria geometriaEsperada() {
        return TipoGeometria.POLIGONO;
    }

    // a malha e a propria base territorial: nao e recortada
    @Override
    public boolean exigeDentroDoParana() {
        return false;
    }
}
