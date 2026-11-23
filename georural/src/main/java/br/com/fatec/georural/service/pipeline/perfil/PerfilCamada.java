package br.com.fatec.georural.service.pipeline.perfil;

import org.locationtech.jts.geom.Geometry;

import java.util.List;
import java.util.Map;

// descreve o que uma camada espera; nao tem logica, so declaracao
public interface PerfilCamada {

    // codigo do TIPO_CONJUNTO que este perfil atende
    String tipoConjunto();

    List<CampoCamada> campos();

    // campo que identifica a feicao na quarentena e na deteccao de duplicidade
    String campoIdentificador();

    TipoGeometria geometriaEsperada();

    // identificador final da feicao. Por padrao e o proprio campo; camada sem campo
    // identificador (ex.: focos de calor) compoe um a partir do conteudo, para a
    // deteccao de duplicidade e o hash da versao enxergarem cada feicao
    default String identificadorDe(Map<String, Object> campos, Geometry geometria,
                                   String identificadorDoCampo) {
        return identificadorDoCampo;
    }

    // se false, a feicao sem geometria ainda e aceita
    default boolean exigeGeometria() {
        return true;
    }

    // se true, a feicao fora do limite do Parana e rejeitada
    default boolean exigeDentroDoParana() {
        return true;
    }

    default String rotulo() {
        return tipoConjunto();
    }

    // nomes de camada que este perfil atende, para um zip do CAR poder ter imovel e
    default List<String> nomesDeCamada() {
        return List.of();
    }

    // ordem em que a camada e carregada quando a rodada tem varias
    default int ordemNoTratamento() {
        return 50;
    }
}
