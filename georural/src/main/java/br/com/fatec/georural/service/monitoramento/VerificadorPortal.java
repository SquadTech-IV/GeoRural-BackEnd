package br.com.fatec.georural.service.monitoramento;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class VerificadorPortal implements VerificadorComponente {

    public static final String NOME = "portal";

    private final SondaHttp sonda;

    @Value("${georural.monitoramento.portal.url:}")
    private String url;

    @Value("${georural.monitoramento.portal.limite-ms:2000}")
    private long limiteMs;

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public ResultadoVerificacao verificar() {
        if (url == null || url.isBlank()) {
            return ResultadoVerificacao.naoConfigurado(NOME,
                    "georural.monitoramento.portal.url nao definido. O portal e servido fora "
                            + "desta aplicacao, entao so da para verificar com a URL publica dele.");
        }
        return sonda.verificar(NOME, url, Duration.ofMillis(limiteMs));
    }
}
