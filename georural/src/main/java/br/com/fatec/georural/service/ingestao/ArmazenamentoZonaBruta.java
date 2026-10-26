package br.com.fatec.georural.service.ingestao;

public interface ArmazenamentoZonaBruta {

    String salvar(Long conjuntoId, String nomeArquivo, byte[] conteudo);
}