package br.com.fatec.georural.service.monitoramento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificadorBanco implements VerificadorComponente {

    public static final String NOME = "banco";

    private final JdbcTemplate jdbc;

    // acima disso o banco responde, mas esta lento
    @Value("${georural.monitoramento.banco.limite-ms:1500}")
    private long limiteMs;

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public ResultadoVerificacao verificar() {
        long inicio = System.nanoTime();
        try {
            jdbc.queryForObject("SELECT 1 FROM DUAL", Integer.class);
            long ms = decorrido(inicio);
            if (limiteMs > 0 && ms > limiteMs) {
                return ResultadoVerificacao.degradado(NOME,
                        "Oracle respondeu em " + ms + " ms, acima do limite de " + limiteMs + " ms.", ms);
            }
            return ResultadoVerificacao.noAr(NOME, "Oracle respondeu em " + ms + " ms.", ms);
        } catch (RuntimeException e) {
            long ms = decorrido(inicio);
            log.warn("Monitoramento: banco fora do ar: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return ResultadoVerificacao.fora(NOME, mensagemDe(e), ms);
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
