package br.com.fatec.georural.dto.response.linhagem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// O resultado de onde a linhagem parte.
public record ResultadoLinhagemResponse(
        Long id,
        String indicador,
        String indicadorNome,
        String unidade,
        Long imovelId,
        String codigoCar,
        String nomeImovel,
        BigDecimal areaTotalHa,
        Long municipioId,
        String municipio,
        String uf,
        BigDecimal percentual,
        BigDecimal hectares,
        BigDecimal absoluto,
        String situacao,
        boolean vigente,
        LocalDateTime calculadoEm
) {}
