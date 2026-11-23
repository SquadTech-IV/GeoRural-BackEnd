package br.com.fatec.georural.dto.response.isap;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record IsapImovelConsultaResponse(
        Long imovelId,
        String codigoCar,
        String nomeImovel,
        String municipio,
        BigDecimal areaImovelHa,
        Long versaoId,
        Long versaoNumero,
        String regraVersao,
        LocalDateTime dataCalculo,
        String avisoDuplaContagem,
        List<IsapCategoriaResumoResponse> resumo,
        List<IsapFeicaoDetalheResponse> detalhes
) {}