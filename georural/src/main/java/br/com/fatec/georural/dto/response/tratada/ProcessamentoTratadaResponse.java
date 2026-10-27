package br.com.fatec.georural.dto.response.tratada;

import java.time.LocalDateTime;

/**
 * Informações do processamento (EXECUCAO_PIPELINE) para a tela da zona tratada.
 * Os nomes batem com o que prepararInformacoesProcessamento já lê (status, etapa,
 * inicio, fim, duracaoSeg, usuarioNome, registros*, versaoNumero, execucaoId).
 */
public record ProcessamentoTratadaResponse(
        Long execucaoId,
        String status,
        String etapa,
        LocalDateTime inicio,
        LocalDateTime fim,
        Long duracaoSeg,
        String usuarioNome,
        Long registrosRecebidos,
        Long registrosProcessados,
        Long registrosAprovados,
        Long registrosRejeitados,
        Long versaoNumero
) {}
