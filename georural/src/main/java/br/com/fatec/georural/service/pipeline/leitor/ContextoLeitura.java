package br.com.fatec.georural.service.pipeline.leitor;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;

import java.io.InputStream;
import java.util.function.Supplier;


public record ContextoLeitura(
        FormatoArquivo formato,
        String nome,
        String caminhoInterno,
        Supplier<InputStream> abrirObjeto,
        Integer srsEsperado) {

    public boolean dentroDeContainer() {
        return caminhoInterno != null && !caminhoInterno.isBlank();
    }

    /** Abre um fluxo novo sobre o objeto. Quem chama fecha. */
    public InputStream abrir() {
        return abrirObjeto.get();
    }
}
