package br.com.fatec.georural.dto.response.trajetoria;

import java.time.LocalDateTime;

public record TrajetoriaEvento(
        String zona,
        String tipo,
        LocalDateTime dataHora,
        String status,
        String descricao
) {}
