package br.com.fatec.georural.dto.response.trajetoria;

import java.time.LocalDateTime;

public record StatusAtualResponse(
        Long conjuntoId,
        String conjuntoNome,
        String zonaAtual,
        String situacao,
        LocalDateTime ultimaAtualizacao
) {}
