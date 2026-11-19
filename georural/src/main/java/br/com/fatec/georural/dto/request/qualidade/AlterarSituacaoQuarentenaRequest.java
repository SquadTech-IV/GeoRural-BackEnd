package br.com.fatec.georural.dto.request.qualidade;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AlterarSituacaoQuarentenaRequest(
        @Schema(description = "Nova situação do registro na quarentena (transições finais: CORRIGIDO ou DESCARTADO)",
                example = "CORRIGIDO", requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty("qua_situacao")
        @JsonAlias("situacao")
        String situacao
) {
}