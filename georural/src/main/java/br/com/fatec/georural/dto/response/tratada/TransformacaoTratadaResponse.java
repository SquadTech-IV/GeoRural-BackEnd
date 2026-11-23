package br.com.fatec.georural.dto.response.tratada;

import java.time.LocalDateTime;

/**
 * Um passo de transformação aplicado no tratamento, já com rótulo amigável e o efeito
 * (entrada -> saída) extraído da descrição registrada pelo pipeline.
 * Campos 'nome', 'descricao', 'situacao' e 'data' são os que o componente
 * HistoricoTransformacoes/prepararHistoricoTransformacoes consome.
 */
public record TransformacaoTratadaResponse(
        Long id,
        Integer ordem,
        String nome,
        String descricao,
        String situacao,
        LocalDateTime data,
        Long entrada,
        Long saida
) {}
