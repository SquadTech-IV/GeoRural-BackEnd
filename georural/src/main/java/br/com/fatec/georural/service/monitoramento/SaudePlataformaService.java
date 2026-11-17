package br.com.fatec.georural.service.monitoramento;

import br.com.fatec.georural.dto.response.monitoramento.SaudePlataformaResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// compoe o estado dos componentes. Nao conhece nenhum deles: recebe a lista de
// VerificadorComponente, igual ao RegistroPerfis com os PerfilCamada
@Slf4j
@Service
public class SaudePlataformaService {

    // ordem fixa na resposta, para a tela nao ficar dancando
    private static final List<String> ORDEM = List.of(
            VerificadorApi.NOME, VerificadorBanco.NOME, VerificadorArmazenamento.NOME,
            VerificadorAirflow.NOME, VerificadorPortal.NOME);

    private final List<VerificadorComponente> verificadores;

    @Value("${georural.monitoramento.cache-segundos:10}")
    private long cacheSegundos;

    private volatile SaudePlataformaResponse ultima;

    public SaudePlataformaService(List<VerificadorComponente> verificadores) {
        this.verificadores = verificadores.stream()
                .sorted(Comparator.comparingInt(v -> {
                    int i = ORDEM.indexOf(v.nome());
                    return i < 0 ? ORDEM.size() : i;
                }))
                .toList();
        log.info("Monitoramento: {} componente(s) verificado(s): {}", this.verificadores.size(),
                this.verificadores.stream().map(VerificadorComponente::nome).toList());
    }

    public SaudePlataformaResponse consultar() {
        SaudePlataformaResponse cacheada = ultima;
        if (valeOCache(cacheada)) {
            return cacheada;
        }
        SaudePlataformaResponse nova = SaudePlataformaResponse.de(verificarTodos());
        ultima = nova;
        return nova;
    }

    // forca a verificacao, ignorando o cache
    public SaudePlataformaResponse consultarAgora() {
        SaudePlataformaResponse nova = SaudePlataformaResponse.de(verificarTodos());
        ultima = nova;
        return nova;
    }

    private boolean valeOCache(SaudePlataformaResponse cacheada) {
        return cacheSegundos > 0 && cacheada != null
                && Duration.between(cacheada.verificadoEm(), LocalDateTime.now())
                        .getSeconds() < cacheSegundos;
    }

    // em paralelo, com thread virtual: um componente travado nao soma o tempo dos outros.
    // Sequencial, cinco componentes a 2s de limite dariam 10s de espera na tela
    private List<ResultadoVerificacao> verificarTodos() {
        List<Callable<ResultadoVerificacao>> tarefas = verificadores.stream()
                .map(v -> (Callable<ResultadoVerificacao>) () -> verificarUm(v))
                .toList();

        List<ResultadoVerificacao> resultados = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ResultadoVerificacao>> futuros = tarefas.stream()
                    .map(executor::submit)
                    .toList();
            for (int i = 0; i < futuros.size(); i++) {
                resultados.add(colher(futuros.get(i), verificadores.get(i)));
            }
        }
        return resultados;
    }

    private ResultadoVerificacao verificarUm(VerificadorComponente verificador) {
        try {
            return verificador.verificar();
        } catch (RuntimeException e) {
            // verificador com defeito nao pode derrubar a consulta inteira
            log.error("Monitoramento: o verificador de '{}' falhou: {}", verificador.nome(),
                    e.getMessage(), e);
            return ResultadoVerificacao.fora(verificador.nome(),
                    "A verificacao falhou: " + e.getClass().getSimpleName(), 0L);
        }
    }

    private ResultadoVerificacao colher(Future<ResultadoVerificacao> futuro,
                                        VerificadorComponente verificador) {
        try {
            return futuro.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultadoVerificacao.fora(verificador.nome(), "Verificacao interrompida.", 0L);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            return ResultadoVerificacao.fora(verificador.nome(),
                    "A verificacao falhou: " + causa.getClass().getSimpleName(), 0L);
        }
    }
}
