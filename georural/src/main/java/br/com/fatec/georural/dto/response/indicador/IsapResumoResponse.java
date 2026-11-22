package br.com.fatec.georural.dto.response.indicador;

import br.com.fatec.georural.entity.ResultadoIsapResumo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record IsapResumoResponse(
        Long resumoId,
        Long imovelId,
        String codigoCar,
        String nomeImovel,
        String municipio,
        Long versaoId,
        Long versaoNumero,
        BigDecimal areaImovelHa,
        BigDecimal areaSobrepostaTotalHa,
        BigDecimal percentualTotal,
        String situacao,
        Integer qtdCategorias,
        Integer qtdAreas,
        boolean vigente,
        LocalDateTime dataCalculo,
        List<IsapDetalheAreaResponse> detalhes
) {
    public static IsapResumoResponse de(ResultadoIsapResumo r, List<IsapDetalheAreaResponse> detalhes) {
        String nomeMunicipio = (r.getImovel() != null && r.getImovel().getMunicipio() != null)
                ? r.getImovel().getMunicipio().getNome()
                : null;

        return new IsapResumoResponse(
                r.getId(),
                r.getImovel() != null ? r.getImovel().getId() : null,
                r.getImovel() != null ? r.getImovel().getCodigoCar() : null,
                r.getImovel() != null ? r.getImovel().getNome() : null,
                nomeMunicipio,
                r.getVersao() != null ? r.getVersao().getId() : null,
                r.getVersao() != null ? r.getVersao().getNumero() : null,
                r.getAreaImovelHa(),
                r.getAreaSobrepostaTotalHa(),
                r.getPercentualTotal(),
                r.getSituacao(),
                r.getQtdCategorias(),
                r.getQtdAreas(),
                "S".equalsIgnoreCase(r.getVigente()),
                r.getDataCalculo(),
                detalhes != null ? detalhes : List.of()
        );
    }
}