package br.com.fatec.georural.dto.response.qualidade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Qualidade acumulada de um conjunto.
public record ResumoConjuntoResponse(
        Long conjuntoId,
        String conjunto,
        String tipoCamada,
        String fonte,
        long validacoes,
        long aprovadas,
        long rejeitadas,
        long totalRegistros,
        long registrosValidos,
        long registrosInvalidos,
        BigDecimal percentualAprovacao,
        long emQuarentena,
        LocalDateTime ultimaValidacao
) {}
