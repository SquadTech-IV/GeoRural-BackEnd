package br.com.fatec.georural.dto.response.indicador;

import java.time.LocalDateTime;
import java.util.List;

// Regra de calculo em vigor para um indicador.
public record RegraVigenteResponse(
        Long id,
        String versao,
        String formula,
        String descricao,
        String autor,
        LocalDateTime criadaEm,
        List<ParametroRegraResponse> parametros
) {}
