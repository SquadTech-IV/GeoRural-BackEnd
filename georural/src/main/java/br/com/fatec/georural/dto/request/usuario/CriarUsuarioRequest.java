package br.com.fatec.georural.dto.request.usuario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CriarUsuarioRequest(
        @Schema(description = "Nome completo do usuário", example = "Maria Silva", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome do usuário é obrigatório.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,

        @Schema(description = "E-mail corporativo único do usuário", example = "maria.silva@georural.gov.br", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O e-mail do usuário é obrigatório.")
        @Email(message = "Informe um endereço de e-mail válido.")
        @Size(max = 160, message = "O e-mail deve ter no máximo 160 caracteres.")
        String email,

        @Schema(description = "Identificador do perfil de acesso (PERFIL.per_id)", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O perfil de acesso (perfilId) é obrigatório.")
        Long perfilId
) {
}