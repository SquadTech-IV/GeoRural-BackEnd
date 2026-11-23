package br.com.fatec.georural.dto.response.monitoramento;

import java.time.LocalDateTime;
import java.util.List;

// tempo de resposta por rota, com as lentas em destaque
public record DesempenhoResponse(
        LocalDateTime medidoEm,
        double limiteSegundos,
        Totais totais,
        List<Rota> rotas,
        List<String> observacoes
) {

    public record Totais(
            int rotasObservadas,
            int rotasAcimaDoLimite,
            long chamadas,
            long erros,
            Double mediaGeralMs
    ) {
    }

    public record Rota(
            String metodo,
            String rota,
            long chamadas,
            long erros,
            double mediaMs,
            double maximoMs,
            // so vem preenchido se os percentis estiverem habilitados nas propriedades
            Double p95Ms,
            Double p99Ms,
            boolean acimaDoLimite
    ) {
    }
}
