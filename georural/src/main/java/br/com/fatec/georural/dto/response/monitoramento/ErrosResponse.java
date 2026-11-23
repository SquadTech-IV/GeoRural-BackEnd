package br.com.fatec.georural.dto.response.monitoramento;

import java.time.LocalDateTime;
import java.util.List;

// erros recentes da API, com busca por texto
public record ErrosResponse(
        LocalDateTime consultadoEm,
        long total,
        int limite,
        int deslocamento,
        List<Resumo> porRota,
        List<Erro> erros,
        List<String> observacoes
) {

    public record Erro(
            Long id,
            LocalDateTime dataHora,
            String metodo,
            String rota,
            int status,
            String excecao,
            String mensagem,
            String pilha,
            String ipOrigem,
            String correlacao,
            Long duracaoMs
    ) {
    }

    // as rotas que mais erram no periodo, para a tela mostrar antes da lista
    public record Resumo(
            String metodo,
            String rota,
            long erros,
            LocalDateTime ultimoEm
    ) {
    }
}
