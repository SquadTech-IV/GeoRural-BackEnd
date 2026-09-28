package br.com.fatec.georural.dto.response.imovel;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record ImovelGeoJsonResponse(
        String type,
        @JsonRawValue String geometry,
        ImovelProperties properties
) {}