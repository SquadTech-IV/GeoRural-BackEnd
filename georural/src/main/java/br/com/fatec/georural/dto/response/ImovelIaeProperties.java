package br.com.fatec.georural.dto.response;

import java.math.BigDecimal;

public record ImovelIaeProperties(
        String codigoCar,
        BigDecimal areaTotal,
        String municipio,
        IaeResultadoResponse iae) {
}