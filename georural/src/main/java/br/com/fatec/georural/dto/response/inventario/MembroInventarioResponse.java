package br.com.fatec.georural.dto.response.inventario;

import java.time.LocalDateTime;

/** Um conjunto de dados encontrado dentro de um arquivo container. */
public record MembroInventarioResponse(
        Long arquivoId,
        String nome,
        String caminhoInterno,
        String formato,
        Long tamanhoBytes,
        Integer srsDeclarado,
        Integer srsUtilizado,
        String origemDoSrs,
        String situacao,
        boolean processado,
        LocalDateTime processadoEm) {
}
