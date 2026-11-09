package br.com.fatec.georural.dto.response.linhagem;

import java.time.LocalDateTime;
import java.util.List;

// Regra de calculo aplicada ao resultado.
public record RegraLinhagemResponse(
        Long id,
        String versao,
        String formula,
        String descricao,
        String autor,
        String motivo,
        boolean vigente,
        LocalDateTime criadaEm,
        List<ParametroLinhagemResponse> parametros
) {}
