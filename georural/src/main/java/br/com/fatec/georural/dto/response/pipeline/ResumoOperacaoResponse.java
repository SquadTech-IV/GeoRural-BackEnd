package br.com.fatec.georural.dto.response.pipeline;

import java.time.LocalDateTime;

// numeros do topo do painel da operacao
public record ResumoOperacaoResponse(
        int dias,
        LocalDateTime desde,
        long conjuntosPublicados,
        long execucoes,
        long execucoesAutomaticas,
        long execucoesManuais,
        long execucoesFalhadas,
        LocalDateTime falhaMaisAntiga,
        long versoesAguardandoPublicacao,
        long quarentenaPendente,
        Long duracaoMediaSeg,
        String duracaoMedia) {
}
