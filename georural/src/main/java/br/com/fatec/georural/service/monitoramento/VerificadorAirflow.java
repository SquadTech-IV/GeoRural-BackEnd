package br.com.fatec.georural.service.monitoramento;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Airflow pela API dele. Sem base-url configurada o estado e NAO_CONFIGURADO:
// dizer "fora do ar" sem nem ter endereco seria mentira
@Service
@RequiredArgsConstructor
public class VerificadorAirflow implements VerificadorComponente {

    public static final String NOME = "airflow";

    private final SondaHttp sonda;

    @Value("${georural.airflow.base-url:}")
    private String baseUrl;

    // Airflow 2.x; no Airflow 3 o caminho e /api/v2/monitor/health
    @Value("${georural.airflow.caminho-saude:/api/v1/health}")
    private String caminhoSaude;

    @Value("${georural.monitoramento.airflow.limite-ms:2000}")
    private long limiteMs;

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public ResultadoVerificacao verificar() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return ResultadoVerificacao.naoConfigurado(NOME,
                    "georural.airflow.base-url nao definido: nao da para verificar o Airflow.");
        }
        String url = baseUrl.replaceAll("/+$", "") + caminhoSaude;
        return sonda.verificar(NOME, url, Duration.ofMillis(limiteMs));
    }
}
