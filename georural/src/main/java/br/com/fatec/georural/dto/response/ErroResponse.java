package br.com.fatec.georural.dto.response;

import java.time.LocalDateTime;

public record ErroResponse(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String caminho
) {}