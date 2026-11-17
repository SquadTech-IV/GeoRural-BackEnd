package br.com.fatec.georural.dto.response.pipeline;

import java.time.LocalDateTime;
import java.util.List;

// O detalhe de uma rodada do pipeline, etapa por etapa.
public record RodadaDetalheResponse(
        String dagRunId,
        Long conjuntoId,
        String conjunto,
        String tipoCamada,
        String fonte,
        Long arquivoBrutoId,
        String arquivo,
        String competencia,
        boolean automatica,
        String disparadoPor,
        String situacao,
        String etapaAtual,
        int etapasAlcancadas,
        int tentativasTotais,
        int reexecucoes,
        long registrosRecebidos,
        long registrosRejeitados,
        Long versaoGeradaId,
        LocalDateTime inicio,
        LocalDateTime fim,
        Long duracaoSeg,
        List<EtapaDaRodadaResponse> etapas) {
}
