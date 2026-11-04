package br.com.fatec.georural.service.pipeline.validacao;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;

// um arquivo (ou camada de dentro de um zip) a ser validado
public record AlvoValidacao(
        Long arquivoId,
        String nome,
        String caminhoInterno,
        String pathArmazenamento,
        FormatoArquivo formato,
        Integer srsUtilizado,
        boolean srsLidoDoArquivo) {

    public String rotulo() {
        return caminhoInterno != null ? caminhoInterno : nome;
    }
}
