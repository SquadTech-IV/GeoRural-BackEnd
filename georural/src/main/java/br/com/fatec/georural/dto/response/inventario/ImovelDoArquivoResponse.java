package br.com.fatec.georural.dto.response.inventario;

import java.math.BigDecimal;
import java.util.List;

// pagina de imoveis produzidos por um arquivo
public record ImovelDoArquivoResponse(
        long total,
        int pagina,
        int tamanho,
        List<Item> itens) {

    public record Item(
            Long imovelId,
            String codigoCar,
            String nome,
            String nomeOrigem,
            String rotulo,
            String municipio,
            String codigoIbge,
            BigDecimal areaTotalHa,
            Long versaoId) {
    }
}
