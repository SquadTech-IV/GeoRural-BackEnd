package br.com.fatec.georural.dto.request.usuario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record EditarUsuarioRequest(
        @Schema(description = "Novo nome completo do usuário (opcional)", example = "Maria Silva Souza")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,

        @Schema(description = "Novo identificador do perfil de acesso (PERFIL.per_id) (opcional)", example = "2")
        Long perfilId
) {
}