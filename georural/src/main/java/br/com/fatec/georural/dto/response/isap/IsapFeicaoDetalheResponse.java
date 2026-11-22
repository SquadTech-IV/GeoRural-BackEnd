package br.com.fatec.georural.dto.response.isap;

import br.com.fatec.georural.entity.ResultadoIsapDetalhe;

import java.math.BigDecimal;

public record IsapFeicaoDetalheResponse(
        Long areaProtegidaId,
        String categoria,
        String nomeFeicao,
        String tipoFeicao,
        String codigoFeicao,
        BigDecimal areaHa,
        BigDecimal pct
) {
    public static IsapFeicaoDetalheResponse de(ResultadoIsapDetalhe d) {
        return new IsapFeicaoDetalheResponse(
                d.getAreaProtegida() != null ? d.getAreaProtegida().getId() : null,
                d.getCategoria(),
                d.getNomeFeicao(),
                d.getTipoFeicao(),
                d.getCodigoFeicao(),
                d.getAreaHa(),
                d.getPctSobreposicao()
        );
    }
}