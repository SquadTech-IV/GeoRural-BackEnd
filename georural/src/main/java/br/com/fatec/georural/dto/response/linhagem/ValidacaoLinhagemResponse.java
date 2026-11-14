package br.com.fatec.georural.dto.response.linhagem;

import java.time.LocalDateTime;

// O portao de qualidade pelo qual o arquivo de origem passou.
public record ValidacaoLinhagemResponse(
        Long validacaoId,
        Long arquivoId,
        String arquivo,
        Long execucaoId,
        String status,
        Long totalRegistros,
        Long registrosValidos,
        Long registrosInvalidos,
        long emQuarentena,
        LocalDateTime validadoEm
) {}
