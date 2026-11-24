package br.com.fatec.georural.service.pipeline.perfil;

import org.springframework.stereotype.Component;

import java.util.List;

// APP declarada no CAR (camada Area_de_Preservacao_Permanente do SICAR)
@Component
public class PerfilApp implements PerfilCamada {

    @Override
    public String tipoConjunto() {
        return "APP";
    }

    @Override
    public String rotulo() {
        return "Área de Preservação Permanente";
    }

    @Override
    public List<CampoCamada> campos() {
        return List.of(
                // obrigatorio porque APP.imo_id e NOT NULL: sem o CAR nao ha a quem ligar.
                // 'recibo' e o nome do campo no pacote por imovel do SICAR
                CampoCamada.obrigatorio("codigo_car", TipoCampo.TEXTO,
                        "recibo", "cod_imovel", "codigo", "cod_car", "car"),
                // 'tema' distingue APP de curso d'agua, de nascente, de topo de morro
                CampoCamada.opcional("tipo", TipoCampo.TEXTO,
                        "tema", "ind_tipo", "tipo_app", "des_tipo"),
                CampoCamada.opcional("area_declarada", TipoCampo.DECIMAL,
                        "num_area", "area", "area_ha", "num_area_h"));
    }

    @Override
    public int ordemNoTratamento() {
        return 28;
    }

    @Override
    public List<String> nomesDeCamada() {
        return List.of("APP", "AREA_DE_PRESERVACAO_PERMANENTE",
                "AREA_PRESERVACAO_PERMANENTE");
    }

    // null de proposito: o pacote do SICAR traz varias APP do mesmo imovel, todas com o
    // mesmo recibo. Usar o recibo como identificador jogaria todas menos a primeira na
    // quarentena por duplicidade -- foi o que aconteceu enquanto a camada caia no
    // PerfilImovel. Sem campo identificador, cada feicao e identificada pelo conteudo.
    @Override
    public String campoIdentificador() {
        return null;
    }

    @Override
    public TipoGeometria geometriaEsperada() {
        return TipoGeometria.POLIGONO;
    }
}
