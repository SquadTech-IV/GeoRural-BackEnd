package br.com.fatec.georural.dto.request.qualidade;

import io.swagger.v3.oas.annotations.media.Schema;

public record AlterarSituacaoQuarentenaRequest(
        @Schema(description = "Nova situação do registro na quarentena (ex: PENDENTE, RESOLVIDO, DESCARTADO, REPROCESSADO)",
                example = "RESOLVIDO", requiredMode = Schema.RequiredMode.REQUIRED)
        String situacao,

        @Schema(description = "Observação ou justificativa opcional para registro na auditoria",
                example = "Geometria corrigida e arquivo reenviado pelo operador")
        String observacao
) {
}