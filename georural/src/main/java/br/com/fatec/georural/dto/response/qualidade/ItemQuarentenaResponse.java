package br.com.fatec.georural.dto.response.qualidade;

import java.time.LocalDateTime;

// Um registro barrado, como aparece na amostra do relatorio.
public record ItemQuarentenaResponse(
        Long id,
        String regra,
        String severidade,
        String etapa,
        String identificador,
        Integer linhaOrigem,
        String colunaErro,
        String valorEncontrado,
        String valorEsperado,
        String motivo,
        String situacao,
        LocalDateTime registradoEm,
        LocalDateTime resolvidoEm,
        Object dadoOriginal
) {}
