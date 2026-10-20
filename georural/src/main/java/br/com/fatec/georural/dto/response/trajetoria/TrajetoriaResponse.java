package br.com.fatec.georural.dto.response.trajetoria;

import java.util.List;

public record TrajetoriaResponse(
        Long conjuntoId,
        String conjuntoNome,
        String zonaAtual,
        List<TrajetoriaEvento> eventos
) {}
