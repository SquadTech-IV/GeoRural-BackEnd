package br.com.fatec.georural.dto.response.inventario;

import java.util.List;
import java.util.Map;

public record PreviaArquivoResponse(
        Long arquivoId,
        String nome,
        String caminhoInterno,
        String formato,
        String formatoRotulo,
        Integer srsUsado,
        String origemDoSrs,
        String tipoGeometria,
        long feicoesLidas,
        // parou no limite pedido; pode haver mais feicoes no arquivo
        boolean truncado,
        List<String> colunas,
        List<Map<String, String>> amostra,
        String observacao) {
}
