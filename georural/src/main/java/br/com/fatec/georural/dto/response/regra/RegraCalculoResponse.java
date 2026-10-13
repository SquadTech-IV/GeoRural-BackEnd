package br.com.fatec.georural.dto.response.regra;

import br.com.fatec.georural.entity.ParametroRegraCalculo;
import br.com.fatec.georural.entity.RegraCalculo;

import java.time.LocalDateTime;
import java.util.List;

public record RegraCalculoResponse(
        Long id,
        String indicadorSigla,
        String versao,
        String formula,
        String descricao,
        String autor,
        String motivo,
        String vigente,
        LocalDateTime dataCriacao,
        List<ParametroResponse> parametros
) {
    public static RegraCalculoResponse de(RegraCalculo regra, List<ParametroRegraCalculo> parametros) {
        return new RegraCalculoResponse(
                regra.getId(),
                regra.getIndicador().getSigla(),
                regra.getVersao(),
                regra.getFormula(),
                regra.getDescricao(),
                regra.getAutor(),
                regra.getMotivo(),
                regra.getVigente(),
                regra.getDataCriacao(),
                parametros.stream().map(ParametroResponse::de).toList());
    }
}