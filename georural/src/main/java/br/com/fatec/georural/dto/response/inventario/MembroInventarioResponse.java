package br.com.fatec.georural.dto.response.inventario;

import java.time.LocalDateTime;

// uma camada de dentro do pacote; perfil diz por quais regras ela vai ser lida
public record MembroInventarioResponse(
        Long arquivoId,
        String nome,
        String caminhoInterno,
        String formato,
        Long tamanhoBytes,
        Integer srsDeclarado,
        Integer srsUtilizado,
        String origemDoSrs,
        String perfil,
        String perfilRotulo,
        String situacao,
        boolean processado,
        LocalDateTime processadoEm) {
}
