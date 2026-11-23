package br.com.fatec.georural.dto.response.pipeline;

import br.com.fatec.georural.dto.response.ContagemResponse;

import java.time.LocalDateTime;
import java.util.List;

// Como o pipeline esta agora, e o que aconteceu na janela consultada.
public record SituacaoPipelineResponse(
        LocalDateTime momento,
        int janelaDias,
        LocalDateTime desde,
        int limiteSegundos,
        List<ExecucaoEmAndamentoResponse> emAndamento,
        List<ContagemResponse> porStatus,
        List<ContagemResponse> porEtapa,
        List<FalhaRecenteResponse> ultimasFalhas,
        long rodadasNaJanela,
        long rodadasAutomaticas,
        long rodadasManuais,
        LocalDateTime ultimaConclusao
) {}
