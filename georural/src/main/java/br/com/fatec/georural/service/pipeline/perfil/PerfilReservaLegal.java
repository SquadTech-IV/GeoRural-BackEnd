package br.com.fatec.georural.service.pipeline.perfil;

import org.springframework.stereotype.Component;

import java.util.List;

// reserva legal declarada no CAR (camada RESERVA_LEGAL do SICAR)
@Component
public class PerfilReservaLegal implements PerfilCamada {

    @Override
    public String tipoConjunto() {
        return "RESERVA_LEGAL";
    }

    @Override
    public String rotulo() {
        return "Reserva Legal declarada";
    }

    @Override
    public List<CampoCamada> campos() {
        return List.of(
                CampoCamada.obrigatorio("codigo_car", TipoCampo.TEXTO,
                        "cod_imovel", "codigo", "cod_car", "car"),
                CampoCamada.opcional("area_declarada", TipoCampo.DECIMAL,
                        "num_area", "area", "area_ha", "num_area_h"),
                CampoCamada.opcional("tipo", TipoCampo.TEXTO,
                        "ind_tipo", "tipo_rl", "des_tipo"),
                CampoCamada.opcional("situacao", TipoCampo.TEXTO,
                        "ind_status", "status"));
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
