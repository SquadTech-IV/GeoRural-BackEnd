package br.com.fatec.georural.dto.response.isap;

import br.com.fatec.georural.entity.ResultadoIsapResumo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IsapListaItemResponse(
        Long imovelId,
        String codigoCar,
        String nomeImovel,
        String municipio,
        BigDecimal areaImovelHa,
        Boolean sobrepoe,
        BigDecimal areaSobrepostaTotalHa,
        BigDecimal pctSobreposicaoTotal,
        String status,
        Long versaoId,
        LocalDateTime dataCalculo
) {
    public static IsapListaItemResponse de(ResultadoIsapResumo r) {
        var imovel = r.getImovel();
        Boolean sobrepoeBool = r.getSobrepoe() == null
                ? null
                : "S".equalsIgnoreCase(r.getSobrepoe());

        return new IsapListaItemResponse(
                imovel.getId(),
                imovel.getCodigoCar(),
                imovel.getNome(),
                imovel.getMunicipio() != null ? imovel.getMunicipio().getNome() : null,
                r.getAreaImovelHa(),
                sobrepoeBool,
                r.getAreaSobrepostaHa(),
                r.getPctSobreposicao(),
                r.getStatus(),
                r.getVersao() != null ? r.getVersao().getId() : null,
                r.getDataCalculo()
        );
    }
}