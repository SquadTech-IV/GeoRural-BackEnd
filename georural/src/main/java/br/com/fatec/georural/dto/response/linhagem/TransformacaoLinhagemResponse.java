package br.com.fatec.georural.dto.response.linhagem;

import java.time.LocalDateTime;

// Uma transformacao registrada na trilha, com o que entrou e o que saiu.
public record TransformacaoLinhagemResponse(
        Long execucaoId,
        String etapa,
        Integer ordem,
        String tipo,
        String descricao,
        String entrada,
        String saida,
        LocalDateTime data
) {}
