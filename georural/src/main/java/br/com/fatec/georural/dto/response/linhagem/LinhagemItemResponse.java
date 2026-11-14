package br.com.fatec.georural.dto.response.linhagem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Um resultado na listagem de linhagem.
public record LinhagemItemResponse(
        Long resultadoId,
        String indicador,
        Long imovelId,
        String codigoCar,
        String nomeImovel,
        String municipio,
        String uf,
        BigDecimal percentual,
        BigDecimal hectares,
        String situacao,
        boolean vigente,
        Long versaoId,
        Integer versaoNumero,
        String competencia,
        Long execucaoId,
        String dagRunId,
        boolean temOrigemRastreada,
        LocalDateTime calculadoEm
) {}
