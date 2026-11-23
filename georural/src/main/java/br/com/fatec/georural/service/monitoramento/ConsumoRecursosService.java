package br.com.fatec.georural.service.monitoramento;

import br.com.fatec.georural.dto.response.monitoramento.RecursosResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumoRecursosService {

    private final JdbcTemplate jdbc;

    public RecursosResponse consultar() {
        return new RecursosResponse(LocalDateTime.now(), processador(), memoria(), disco(),
                acervo(), naoMedido());
    }

    private RecursosResponse.Processador processador() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        var os = ManagementFactory.getOperatingSystemMXBean();

        Double usoProcesso = null;
        Double usoSistema = null;
        // os valores de CPU sao da extensao com.sun.management; em JVM que nao a
        // exponha, ficam nulos em vez de quebrar
        if (os instanceof com.sun.management.OperatingSystemMXBean estendido) {
            usoProcesso = pct(estendido.getProcessCpuLoad());
            usoSistema = pct(estendido.getCpuLoad());
        }

        return new RecursosResponse.Processador(
                Runtime.getRuntime().availableProcessors(),
                usoProcesso, usoSistema,
                threads.getThreadCount(), threads.getPeakThreadCount());
    }

    private RecursosResponse.Memoria memoria() {
        MemoryMXBean memoria = ManagementFactory.getMemoryMXBean();
        long heapUsado = memoria.getHeapMemoryUsage().getUsed();
        long heapMaximo = memoria.getHeapMemoryUsage().getMax();

        Long sistemaTotal = null;
        Long sistemaLivre = null;
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean estendido) {
            sistemaTotal = estendido.getTotalMemorySize();
            sistemaLivre = estendido.getFreeMemorySize();
        }

        return new RecursosResponse.Memoria(
                heapUsado,
                heapMaximo,
                heapMaximo > 0 ? arredondar(heapUsado * 100.0 / heapMaximo) : null,
                memoria.getNonHeapMemoryUsage().getUsed(),
                sistemaTotal, sistemaLivre);
    }

    // disco de onde a aplicacao roda; e o mesmo que o DiskSpaceHealthIndicator olha
    private RecursosResponse.Disco disco() {
        Path caminho = Path.of(".").toAbsolutePath().normalize();
        try {
            FileStore store = Files.getFileStore(caminho);
            long total = store.getTotalSpace();
            long usavel = store.getUsableSpace();
            return new RecursosResponse.Disco(caminho.toString(), total, usavel,
                    total > 0 ? arredondar((total - usavel) * 100.0 / total) : null);
        } catch (IOException e) {
            log.warn("Monitoramento: nao foi possivel ler o disco de {}: {}", caminho, e.getMessage());
            return new RecursosResponse.Disco(caminho.toString(), 0L, 0L, null);
        }
    }

    // Tamanho do acervo pelo CATALOGO, nao pelo bucket.
    // Listar o bucket inteiro para somar seria uma listagem de milhares de chaves a
    // cada abertura da tela. E a soma conta so os arquivos sem pai: as camadas de
    // dentro de um zip apontam para o MESMO objeto do pai, e somar todas as linhas
    // contaria o mesmo byte varias vezes.
    private RecursosResponse.Acervo acervo() {
        try {
            return jdbc.queryForObject(
                    " SELECT COUNT(CASE WHEN a.arq_pai_id IS NULL THEN 1 END) AS arquivos, "
                  + "        NVL(SUM(CASE WHEN a.arq_pai_id IS NULL "
                  + "                     THEN a.arq_tamanho_bytes END), 0) AS bytes, "
                  + "        COUNT(CASE WHEN a.arq_pai_id IS NOT NULL THEN 1 END) AS membros "
                  + "   FROM ARQUIVO_BRUTO a "
                  // NVL porque arq_status e anulavel: sem ele, NULL <> 'RECEBENDO' da
                  // UNKNOWN e a linha sairia da conta sem ninguem notar
                  + "  WHERE NVL(a.arq_status, 'DESCONHECIDO') <> 'RECEBENDO' ",
                    (rs, linha) -> new RecursosResponse.Acervo(
                            rs.getLong("arquivos"),
                            rs.getLong("bytes"),
                            rs.getLong("membros"),
                            "Somado pelo catalogo ARQUIVO_BRUTO, so os arquivos sem pai: "
                                    + "camada de dentro de um zip aponta para o mesmo objeto."));
        } catch (RuntimeException e) {
            log.warn("Monitoramento: nao foi possivel medir o acervo: {}", e.getMessage());
            return new RecursosResponse.Acervo(0L, 0L, 0L,
                    "Nao foi possivel consultar o catalogo: " + e.getClass().getSimpleName());
        }
    }

    // honestidade sobre o que esta de fora
    private List<String> naoMedido() {
        List<String> faltando = new ArrayList<>();
        faltando.add("CPU e memoria do contêiner na nuvem: vem do provedor, depende da US#11.");
        faltando.add("Tamanho real dos buckets na OCI: exigiria listar o bucket inteiro ou a "
                + "API de uso do provedor. O numero do acervo aqui e o do catalogo.");
        faltando.add("Zonas quarentena, tratada e publicada: nao tem catalogo proprio de "
                + "tamanho, so a bruta tem.");
        return faltando;
    }

    private static Double pct(double valor) {
        // a JVM devolve negativo quando ainda nao sabe medir
        return valor < 0 ? null : arredondar(valor * 100.0);
    }

    private static Double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
