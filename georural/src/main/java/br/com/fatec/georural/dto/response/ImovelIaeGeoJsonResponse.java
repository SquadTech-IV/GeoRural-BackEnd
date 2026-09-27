package br.com.fatec.georural.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

public record ImovelIaeGeoJsonResponse(
        String type,
        JsonNode geometry,
        ImovelIaeProperties properties) {
}