package br.com.fatec.georural.dto.response.inventario;

import java.math.BigDecimal;

// quantos imoveis aquele arquivo trouxe de cada municipio
public record MunicipioDoArquivoResponse(
        Long municipioId,
        String codigoIbge,
        String nome,
        String uf,
        long imoveis,
        BigDecimal areaTotalHa) {
}
