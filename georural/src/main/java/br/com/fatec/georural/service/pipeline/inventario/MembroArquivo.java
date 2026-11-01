package br.com.fatec.georural.service.pipeline.inventario;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;

import java.util.List;

public record MembroArquivo(
        String caminhoInterno,
        String nome,
        FormatoArquivo formato,
        String hash,
        long tamanhoBytes,
        Integer srsEpsg,
        List<String> partes,
        String observacao) {

    public boolean temVariasPartes() {
        return partes != null && partes.size() > 1;
    }
}
