package br.com.fatec.georural.dto.response.tratada;

import java.time.LocalDateTime;

/**
 * Um item da lista da tela "Zona tratada": uma execução que gravou dados na zona tratada.
 * Os nomes dos campos batem com o que o front (zonaTratadaConsultaService) já lê.
 */
public record ZonaTratadaResumoResponse(
        Long conjuntoId,
        Long execucaoId,
        Long arquivoId,
        String conjuntoNome,
        String fonteNome,
        String tipoNome,
        String situacao,
        String competencia,
        Long aprovados,
        Long tratada,
        LocalDateTime quando
) {}
