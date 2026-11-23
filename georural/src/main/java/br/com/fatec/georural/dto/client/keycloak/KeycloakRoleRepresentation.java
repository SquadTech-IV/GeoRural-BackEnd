package br.com.fatec.georural.dto.client.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KeycloakRoleRepresentation(
        String id,
        String name,
        String description
) {
}