package br.com.fatec.georural.dto.response;

public record ImovelIaeGeoJsonResponse(
        String type,
        String geometry,
        ImovelIaeProperties properties) {
}