package br.com.fatec.georural.service.pipeline.perfil;

import org.springframework.stereotype.Component;

import java.util.List;

// areas protegidas: unidade de conservacao (CNUC), terra indigena (FUNAI), territorio
// quilombola (Incra) e floresta publica. Alimenta o ISAP
@Component
public class PerfilAreaProtegida implements PerfilCamada {

    @Override
    public String tipoConjunto() {
        return "AREA_PROTEGIDA";
    }

    @Override
    public String rotulo() {
        return "Áreas protegidas";
    }

    // os nomes canonicos abaixo sao as chaves que o carregar_area_protegida le do
    // stg_atributos; os demais sao nomes de coluna das fontes (CNUC, FUNAI, Incra)
    @Override
    public List<CampoCamada> campos() {
        return List.of(
                // decide o apr_tipo: UC, TERRA_INDIGENA, QUILOMBOLA ou FLORESTA_PUBLICA
                CampoCamada.opcional("categoria", TipoCampo.TEXTO,
                        "tipo", "cnuc_categ", "categ", "des_categ", "tipo_area"),
                CampoCamada.opcional("subtipo", TipoCampo.TEXTO,
                        "categoria_uc", "grupo", "sub_categ", "grupo_uc", "fase_ti"),
                CampoCamada.opcional("nome", TipoCampo.TEXTO,
                        "nome_uc", "nome_uc1", "nm_uc", "terrai_nom", "nm_comunid",
                        "denominacao"),
                CampoCamada.opcional("codigo", TipoCampo.TEXTO,
                        "cod_cnuc", "cnuc", "cd_uc", "terrai_cod", "objectid", "gid",
                        "fid"),
                CampoCamada.opcional("competencia", TipoCampo.TEXTO,
                        "ano_cria", "ano_ref", "ano"));
    }

    @Override
    public int ordemNoTratamento() {
        return 40;
    }

    @Override
    public List<String> nomesDeCamada() {
        return List.of("AREA_PROTEGIDA", "AREAS_PROTEGIDAS", "UNIDADE_CONSERVACAO",
                "UNIDADES_CONSERVACAO", "CNUC", "TERRA_INDIGENA", "TERRAS_INDIGENAS",
                "QUILOMBOLA", "FLORESTA_PUBLICA");
    }

    // mesmo criterio do PerfilDesmatamento: a fonte traz codigo proprio por feicao
    @Override
    public String campoIdentificador() {
        return "codigo";
    }

    @Override
    public TipoGeometria geometriaEsperada() {
        return TipoGeometria.POLIGONO;
    }
}
