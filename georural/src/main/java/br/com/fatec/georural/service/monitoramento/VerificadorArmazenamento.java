package br.com.fatec.georural.service.monitoramento;

import br.com.fatec.georural.service.storage.ArmazenamentoZonaPort;
import br.com.fatec.georural.service.storage.Zona;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// verifica o Object Storage com uma leitura leve: existe() num caminho de sonda.
// Responder "nao existe" ja prova que o bucket esta alcancavel; o que importa e
// a chamada nao estourar.
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificadorArmazenamento implements VerificadorComponente {

    public static final String NOME = "armazenamento";

    private static final String CAMINHO_SONDA = "_monitoramento/sonda";

    private final ArmazenamentoZonaPort armazenamento;

    @Value("${georural.monitoramento.armazenamento.limite-ms:3000}")
    private long limiteMs;

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public ResultadoVerificacao verificar() {
        long inicio = System.nanoTime();
        try {
            armazenamento.existe(Zona.BRUTA, CAMINHO_SONDA);
            long ms = decorrido(inicio);
            if (limiteMs > 0 && ms > limiteMs) {
                return ResultadoVerificacao.degradado(NOME,
                        "Zona bruta respondeu em " + ms + " ms, acima do limite de "
                                + limiteMs + " ms.", ms);
            }
            return ResultadoVerificacao.noAr(NOME, "Zona bruta respondeu em " + ms + " ms.", ms);
        } catch (RuntimeException e) {
            long ms = decorrido(inicio);
            log.warn("Monitoramento: armazenamento fora do ar: {}: {}",
                    e.getClass().getSimpleName(), e.getMessage());
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
