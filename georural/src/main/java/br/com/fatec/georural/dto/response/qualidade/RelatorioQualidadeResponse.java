package br.com.fatec.georural.dto.response.qualidade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Um relatorio de qualidade: o resultado da validacao de um arquivo.
public record RelatorioQualidadeResponse(
        Long validacaoId,
        Long execucaoId,
        Long arquivoId,
        String arquivo,
        String competencia,
        String formato,
        Long conjuntoId,
        String conjunto,
        String tipoCamada,
        String fonte,
        long totalRegistros,
        long registrosValidos,
        long registrosInvalidos,
        BigDecimal percentualAprovacao,
        String status,
        boolean aprovado,
        long emQuarentena,
        long quarentenaPendente,
        LocalDateTime validadoEm
) {}
