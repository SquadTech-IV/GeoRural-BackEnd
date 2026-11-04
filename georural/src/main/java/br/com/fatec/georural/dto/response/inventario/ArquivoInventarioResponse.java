package br.com.fatec.georural.dto.response.inventario;

import java.time.LocalDateTime;
import java.util.List;

public record ArquivoInventarioResponse(
        Long arquivoId,
        String nome,
        String formato,
        String canalEntrada,
        String competencia,
        Long tamanhoBytes,
        String hash,
        String situacao,
        boolean container,
        int totalMembros,
        int membrosProcessados,
        LocalDateTime recebidoEm,
        List<MembroInventarioResponse> membros) {
}
