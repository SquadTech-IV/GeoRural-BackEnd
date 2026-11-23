package br.com.fatec.georural.dto.response.monitoramento;

import java.time.LocalDateTime;
import java.util.List;

// consumo de recursos da aplicacao e do acervo
public record RecursosResponse(
        LocalDateTime medidoEm,
        Processador processador,
        Memoria memoria,
        Disco disco,
        Acervo acervo,
        // o que nao da para medir de dentro da aplicacao
        List<String> naoMedido
) {

    public record Processador(
            int nucleos,
            // -1 quando a JVM nao soube informar
            Double usoProcessoPct,
            Double usoSistemaPct,
            int threads,
            int threadsPico
    ) {
    }

    public record Memoria(
            long heapUsadoBytes,
            long heapMaximoBytes,
            Double heapUsoPct,
            long foraDoHeapUsadoBytes,
            Long sistemaTotalBytes,
            Long sistemaLivreBytes
    ) {
    }

    public record Disco(
            String caminho,
            long totalBytes,
            long usavelBytes,
            Double usoPct
    ) {
    }

    // o que esta no data lake, contado pelo catalogo (ARQUIVO_BRUTO)
    public record Acervo(
            long arquivos,
            long bytes,
            long membros,
            String observacao
    ) {
    }
}
