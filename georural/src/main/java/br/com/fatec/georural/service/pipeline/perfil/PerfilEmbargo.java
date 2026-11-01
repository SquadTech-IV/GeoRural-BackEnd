package br.com.fatec.georural.service.pipeline.perfil;

import org.springframework.stereotype.Component;

import java.util.List;

// embargo ambiental do IBAMA
@Component
public class PerfilEmbargo implements PerfilCamada {

    @Override
    public String tipoConjunto() {
        return "EMBARGO";
    }

    @Override
    public String rotulo() {
        return "Embargo ambiental (IBAMA)";
    }

    @Override
    public List<CampoCamada> campos() {
        return List.of(
                CampoCamada.obrigatorio("identificador", TipoCampo.TEXTO,
                        "num_tad", "tad", "numero_tad", "num_termo", "objectid", "id"),
                CampoCamada.opcional("area_embargada", TipoCampo.DECIMAL,
                        "qtd_area_e", "area_embargada_ha", "area", "num_area"),
                CampoCamada.opcional("data_embargo", TipoCampo.DATA,
                        "dat_embarg", "data_tad", "dt_embargo"),
                CampoCamada.opcional("municipio_nome", TipoCampo.TEXTO,
                        "municipio", "nom_munici", "nm_mun"),
                CampoCamada.opcional("uf", TipoCampo.TEXTO,
                        "estado", "sigla_uf", "cod_estado"),
                CampoCamada.opcional("processo", TipoCampo.TEXTO,
                        "num_proces", "processo_numero", "num_auto"),
                CampoCamada.opcional("descricao", TipoCampo.TEXTO,
                        "des_embarg", "descricao_embargo", "des_infrac"));
    }

    @Override
    public String campoIdentificador() {
        return "identificador";
    }

    // o embargo do IBAMA as vezes vem como ponto, as vezes como poligono
    @Override
    public TipoGeometria geometriaEsperada() {
        return TipoGeometria.QUALQUER;
    }
}
