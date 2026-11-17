package br.com.fatec.georural.dto.response.catalogo;

import java.time.LocalDateTime;

public record AcervoResumo(
        long totalVersoes,
        long totalArquivos,
        long bytesArmazenados,
        LocalDateTime ultimoEnvio
) {}
