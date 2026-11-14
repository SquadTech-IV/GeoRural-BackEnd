package br.com.fatec.georural.dto.response.qualidade;

import java.math.BigDecimal;
import java.util.List;

// Visao agregada da qualidade sob os filtros pedidos.
public record ResumoQualidadeResponse(
        long validacoes,
        long aprovadas,
        long rejeitadas,
        long totalRegistros,
        long registrosValidos,
        long registrosInvalidos,
        BigDecimal percentualAprovacao,
        long emQuarentena,
        List<ResumoConjuntoResponse> porConjunto,
        List<ViolacaoRegraResponse> regrasMaisVioladas
) {}
