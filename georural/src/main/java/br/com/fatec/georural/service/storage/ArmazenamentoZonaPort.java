package br.com.fatec.georural.service.storage;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Acesso ao armazenamento de objetos do data lake, por zona.
 *
 * <p>A interface oferece apenas gravar, ler, listar e verificar existência. Não existe operação de
 * apagar nem de alterar objeto, em nenhuma zona. A gravação nunca sobrescreve: se o objeto já existe,
 * lança {@link br.com.fatec.georural.exception.ObjetoJaExisteException}.
 */
public interface ArmazenamentoZonaPort {

    /**
     * Grava o conteúdo em fluxo, sem carregá-lo inteiro na memória (nas implementações reais).
     *
     * @param tamanho   tamanho do conteúdo em bytes
     * @param metadados metadados do objeto; pode ser {@code null}
     * @throws br.com.fatec.georural.exception.ObjetoJaExisteException se o objeto já existe na zona
     */
    void gravar(Zona zona, String caminho, InputStream conteudo, long tamanho, Map<String, String> metadados);

    /** Devolve o conteúdo do objeto em fluxo. Quem chama deve fechar o stream. */
    InputStream ler(Zona zona, String caminho);

    /** Lista os caminhos dos objetos da zona que começam com o prefixo (todas as páginas). */
    List<String> listar(Zona zona, String prefixo);

    boolean existe(Zona zona, String caminho);
}