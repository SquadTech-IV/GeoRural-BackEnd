package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Resultado de um indicador para um imovel.
public record ResultadoImovelResponse(
        Long id,
        Long imovelId,
        String codigoCar,
        String nomeImovel,
        Long municipioId,
        String municipio,
        String uf,
        String indicador,
        String indicadorNome,
        String unidade,
        Long versaoId,
        Integer versaoNumero,
        String competencia,
        Long execucaoId,
        Long regraId,
        String regraVersao,
        BigDecimal percentual,
        BigDecimal hectares,
        BigDecimal absoluto,
        String situacao,
        boolean vigente,
        LocalDateTime calculadoEm,
        Object detalhes
) {}
