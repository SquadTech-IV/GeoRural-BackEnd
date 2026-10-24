package br.com.fatec.georural.dto.ingestao;

public record ConteudoRecebido(
        FormatoRespostaApi formato,
        long quantidadeRegistros,
        long tamanhoBytes,
        boolean bemFormado,
        byte[] conteudo
) {
}