package br.com.fatec.georural.dto.response.imovel;

import java.math.BigDecimal;

public record ImovelProperties(
        String codigoCar,
        BigDecimal areaTotal,
        String municipio,
        ImovelIaeResumo iae
) {}