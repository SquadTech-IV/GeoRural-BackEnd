package br.com.fatec.georural.service.pipeline.leitor;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;

import java.util.function.Consumer;

public interface LeitorFeicoes {

    boolean suporta(FormatoArquivo formato);

    /**
     * @return quantas feicoes foram lidas.
     */
    long ler(ContextoLeitura contexto, Consumer<FeicaoLida> consumidor);
}
