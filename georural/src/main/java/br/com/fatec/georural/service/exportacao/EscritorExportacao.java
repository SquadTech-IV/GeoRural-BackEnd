package br.com.fatec.georural.service.exportacao;

import java.io.IOException;

public interface EscritorExportacao extends AutoCloseable {

    void inicio() throws IOException;

    void linha(LinhaResultado linha) throws IOException;

    void fim() throws IOException;

    @Override
    void close() throws IOException;
}
