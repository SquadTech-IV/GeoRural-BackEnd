package br.com.fatec.georural.service.monitoramento;

import br.com.fatec.georural.dto.response.monitoramento.DesempenhoResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// tempo de resposta por rota, lido do http.server.requests do Micrometer
@Slf4j
@Service
@RequiredArgsConstructor
public class DesempenhoRotasService {

    private static final String METRICA = "http.server.requests";

    private final MeterRegistry registro;

    //consulta nao deve passar de 3 segundos
    @Value("${georural.monitoramento.desempenho.limite-segundos:3}")
    private double limiteSegundos;

    // so as rotas da API; sem isso o /actuator/health do balanceador domina a lista
    @Value("${georural.monitoramento.desempenho.prefixo:/api}")
    private String prefixo;

    public DesempenhoResponse consultar(Double limiteSegundosPedido) {
        double limite = limiteSegundosPedido != null && limiteSegundosPedido > 0
                ? limiteSegundosPedido
                : limiteSegundos;
        double limiteMs = limite * 1000.0;

        List<Timer> timers = new ArrayList<>(registro.find(METRICA).timers());
        if (timers.isEmpty()) {
            return vazio(limite);
        }

        // uma rota tem VARIOS timers: o Micrometer separa por status, outcome e
        // excecao. Precisa juntar por (metodo, rota) antes de tirar media
        Map<String, Acumulador> porRota = new LinkedHashMap<>();
        for (Timer timer : timers) {
            String rota = tag(timer, "uri");
            String metodo = tag(timer, "method");
            if (rota == null || !interessa(rota)) {
                continue;
            }
            porRota.computeIfAbsent(metodo + " " + rota, k -> new Acumulador(metodo, rota))
                   .somar(timer);
        }

        // acima do limite primeiro, depois da mais lenta para a mais rapida
        Comparator<DesempenhoResponse.Rota> ordem = Comparator
                .comparing(DesempenhoResponse.Rota::acimaDoLimite, Comparator.reverseOrder())
                .thenComparing(DesempenhoResponse.Rota::maximoMs, Comparator.reverseOrder());

        List<DesempenhoResponse.Rota> rotas = porRota.values().stream()
                .map(a -> a.fechar(limiteMs))
                .sorted(ordem)
                .toList();

        long chamadas = rotas.stream().mapToLong(DesempenhoResponse.Rota::chamadas).sum();
        long erros = rotas.stream().mapToLong(DesempenhoResponse.Rota::erros).sum();
        double tempoTotal = rotas.stream()
                .mapToDouble(r -> r.mediaMs() * r.chamadas())
                .sum();

        DesempenhoResponse.Totais totais = new DesempenhoResponse.Totais(
                rotas.size(),
                (int) rotas.stream().filter(DesempenhoResponse.Rota::acimaDoLimite).count(),
                chamadas,
                erros,
                chamadas > 0 ? arredondar(tempoTotal / chamadas) : null);

        return new DesempenhoResponse(LocalDateTime.now(), limite, totais, rotas,
                observacoes(rotas));
    }

    private boolean interessa(String rota) {
        if (prefixo == null || prefixo.isBlank()) {
            return true;
        }
        return rota.startsWith(prefixo);
    }

    private static String tag(Timer timer, String chave) {
        return timer.getId().getTag(chave);
    }

    private DesempenhoResponse vazio(double limite) {
        List<String> notas = new ArrayList<>();
        notas.add("Nenhuma medicao ainda: a metrica " + METRICA + " so aparece depois da "
                + "primeira requisicao atendida desde que a aplicacao subiu.");
        return new DesempenhoResponse(LocalDateTime.now(), limite,
                new DesempenhoResponse.Totais(0, 0, 0L, 0L, null), List.of(), notas);
    }

    private List<String> observacoes(List<DesempenhoResponse.Rota> rotas) {
        List<String> notas = new ArrayList<>();
        notas.add("A media e acumulada desde que esta instancia subiu; o maximo e dos "
                + "ultimos minutos, porque o Micrometer o deixa expirar.");
        notas.add("Os numeros sao desta instancia. Com mais de uma no ar, cada uma responde "
                + "pelas requisicoes que atendeu.");
        if (rotas.stream().allMatch(r -> r.p95Ms() == null)) {
            notas.add("Percentis desligados. Para ligar: "
                    + "management.metrics.distribution.percentiles.http.server.requests=0.95,0.99");
        }
        return notas;
    }

    private static double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    // junta os timers de uma mesma rota
    private static final class Acumulador {

        private final String metodo;
        private final String rota;
        private long chamadas;
        private long erros;
        private double tempoTotalMs;
        private double maximoMs;
        private Double p95Ms;
        private Double p99Ms;

        private Acumulador(String metodo, String rota) {
            this.metodo = metodo;
            this.rota = rota;
        }

        private void somar(Timer timer) {
            long conta = timer.count();
            chamadas += conta;
            tempoTotalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            maximoMs = Math.max(maximoMs, timer.max(TimeUnit.MILLISECONDS));

            String desfecho = timer.getId().getTag("outcome");
            if (desfecho != null && !"SUCCESS".equals(desfecho) && !"REDIRECTION".equals(desfecho)) {
                erros += conta;
            }
            lerPercentis(timer);
        }

        // percentis existem so se estiverem habilitados nas propriedades;
        // sem isso o array vem vazio
        private void lerPercentis(Timer timer) {
            ValueAtPercentile[] valores = timer.takeSnapshot().percentileValues();
            for (ValueAtPercentile v : valores) {
                double ms = v.value(TimeUnit.MILLISECONDS);
                if (Math.abs(v.percentile() - 0.95) < 0.001) {
                    p95Ms = p95Ms == null ? ms : Math.max(p95Ms, ms);
                } else if (Math.abs(v.percentile() - 0.99) < 0.001) {
                    p99Ms = p99Ms == null ? ms : Math.max(p99Ms, ms);
                }
            }
        }

        private DesempenhoResponse.Rota fechar(double limiteMs) {
            double media = chamadas > 0 ? tempoTotalMs / chamadas : 0.0;
            // o criterio e o pico: uma rota que passou de 3s uma vez precisa aparecer,
            // mesmo com media boa
            boolean acima = maximoMs > limiteMs;
            return new DesempenhoResponse.Rota(metodo, rota, chamadas, erros,
                    arredondar(media), arredondar(maximoMs),
                    p95Ms == null ? null : arredondar(p95Ms),
                    p99Ms == null ? null : arredondar(p99Ms),
                    acima);
        }
    }
}
