package br.com.fatec.georural.dto.response.indicador;

import br.com.fatec.georural.entity.ResultadoIsapDetalhe;

import java.math.BigDecimal;

public record IsapDetalheAreaResponse(
        Long detalheId,
        Long areaProtegidaId,
        Long versaoCamadaId,
        Long versaoCamadaNumero,
        String categoria,
        String subcategoria,
        String nomeArea,
        String codigoOrigem,
        String esfera,
        BigDecimal areaSobrepostaHa,
        BigDecimal percentualImovel
) {
    public static IsapDetalheAreaResponse de(ResultadoIsapDetalhe d) {
        return new IsapDetalheAreaResponse(
                d.getId(),
                d.getAreaProtegida() != null ? d.getAreaProtegida().getId() : null,
                d.getVersaoCamada() != null ? d.getVersaoCamada().getId() : null,
                d.getVersaoCamada() != null ? d.getVersaoCamada().getNumero() : null,
                d.getCategoria(),
                d.getSubcategoria(),
                d.getNomeArea(),
                d.getCodigoOrigem(),
                d.getEsfera(),
                d.getAreaSobrepostaHa(),
                d.getPercentualImovel()
        );
    }
}