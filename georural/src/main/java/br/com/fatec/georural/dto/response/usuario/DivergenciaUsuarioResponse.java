package br.com.fatec.georural.dto.response.usuario;

import java.util.Set;

public record DivergenciaUsuarioResponse(
        String tipoDivergencia,
        Long usuarioId,
        String keycloakId,
        String email,
        String nome,
        String perfilOracle,
        Set<String> rolesKeycloak,
        String statusOracle,
        Boolean enabledKeycloak,
        String descricao
) {
}