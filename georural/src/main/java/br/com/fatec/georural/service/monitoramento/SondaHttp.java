package br.com.fatec.georural.service.monitoramento;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// GET simples para saber se um endereco responde.
@Slf4j
@Component
public class SondaHttp {

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ResultadoVerificacao verificar(String componente, String url, Duration tempoLimite) {
        long inicio = System.nanoTime();
        try {
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(url))
                    .timeout(tempoLimite)
                    .header("User-Agent", "georural-monitoramento")
                    .GET()
                    .build();

            HttpResponse<Void> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.discarding());
            long ms = decorrido(inicio);
            int codigo = resposta.statusCode();

            if (codigo >= 200 && codigo < 400) {
                return ResultadoVerificacao.noAr(componente,
                        "Respondeu HTTP " + codigo + " em " + ms + " ms.", ms);
            }
            // responde, mas nao como deveria: degradado, nao fora do ar
            return ResultadoVerificacao.degradado(componente,
                    "Respondeu HTTP " + codigo + " em " + ms + " ms.", ms);

        } catch (IOException e) {
            long ms = decorrido(inicio);
            log.warn("Monitoramento: '{}' nao respondeu em {}: {}", componente, url, e.getMessage());
            return ResultadoVerificacao.fora(componente, mensagemDe(e), ms);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResultadoVerificacao.fora(componente, "Verificacao interrompida.",
                    decorrido(inicio));

        } catch (RuntimeException e) {
            // URI mal formada, por exemplo: e erro de configuracao, nao queda
            return ResultadoVerificacao.naoConfigurado(componente,
                    "Endereco invalido (" + url + "): " + mensagemDe(e));
        }
    }

    private static long decorrido(long inicioNano) {
        return (System.nanoTime() - inicioNano) / 1_000_000L;
    }

    private static String mensagemDe(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
