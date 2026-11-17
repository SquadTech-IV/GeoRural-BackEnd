package br.com.fatec.georural.dto.response.pipeline;

import java.util.List;

// Uma das etapas do fluxo dentro da rodada, com todas as tentativas dela.
public record EtapaDaRodadaResponse(
        String etapa,
        int ordem,
        String descricao,
        boolean implementada,
        boolean alcancada,
        String situacao,
        int tentativas,
        boolean podeReexecutar,
        boolean exigeForcar,
        TentativaEtapaResponse ultima,
        List<TentativaEtapaResponse> historico) {
}
