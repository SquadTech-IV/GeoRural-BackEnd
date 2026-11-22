package br.com.fatec.georural.dto.response.isap;

import br.com.fatec.georural.entity.ResultadoIsapResumo;

import java.math.BigDecimal;

public record IsapCategoriaResumoResponse(
        String categoria,
        Boolean sobrepoe,
        BigDecimal areaSobrepostaHa,
        BigDecimal pctSobreposicao,
        BigDecimal areaImovelHa,
        String filtroAplicado,
        String competenciaCamada,
        Long camadaVersaoId,
        String status
) {
    public static IsapCategoriaResumoResponse de(ResultadoIsapResumo r) {
        Boolean sobrepoeBool = r.getSobrepoe() == null
                ? null
                : "S".equalsIgnoreCase(r.getSobrepoe());

        return new IsapCategoriaResumoResponse(
                r.getCategoria(),
                sobrepoeBool,
                r.getAreaSobrepostaHa(),
                r.getPctSobreposicao(),
                r.getAreaImovelHa(),
                r.getFiltroAplicado(),
                r.getCompetenciaCamada(),
                r.getCamadaVersao() != null ? r.getCamadaVersao().getId() : null,
                r.getStatus()
        );
    }
}