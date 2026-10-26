package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.exception.ObjetoJaExisteException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Implementação em memória da {@link ArmazenamentoZonaPort}, somente para testes unitários.
 * Não é um bean do Spring.
 *
 * <p>Mantém as mesmas regras da implementação real: não sobrescreve, não apaga e não altera objeto.
 */
public class InMemoryArmazenamentoZona implements ArmazenamentoZonaPort {

    private record Objeto(byte[] conteudo, Map<String, String> metadados) {
    }

    private final Map<Zona, ConcurrentSkipListMap<String, Objeto>> zonas = new EnumMap<>(Zona.class);

    public InMemoryArmazenamentoZona() {
        for (Zona zona : Zona.values()) {
            zonas.put(zona, new ConcurrentSkipListMap<>());
        }
    }

    @Override
    public void gravar(Zona zona, String caminho, InputStream conteudo, long tamanho, Map<String, String> metadados) {
        validar(zona, caminho);
        if (conteudo == null) {
            throw new IllegalArgumentException("Stream de conteúdo é obrigatório.");
        }
        byte[] bytes;
        try {
            bytes = conteudo.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o conteúdo a gravar.", e);
        }
        if (bytes.length != tamanho) {
            throw new IllegalArgumentException(
                    "O tamanho informado (" + tamanho + ") difere do conteúdo (" + bytes.length + ").");
        }
        Map<String, String> copia = metadados == null ? Map.of() : Map.copyOf(metadados);
        // putIfAbsent é atômico: dois envios simultâneos ao mesmo caminho não se sobrescrevem.
        if (zonas.get(zona).putIfAbsent(caminho, new Objeto(bytes, copia)) != null) {
            throw new ObjetoJaExisteException(caminho);
        }
    }

    @Override
    public InputStream ler(Zona zona, String caminho) {
        validar(zona, caminho);
        Objeto objeto = zonas.get(zona).get(caminho);
        if (objeto == null) {
            throw new NoSuchElementException("Objeto não encontrado: " + caminho);
        }
        return new ByteArrayInputStream(objeto.conteudo());
    }

    @Override
    public List<String> listar(Zona zona, String prefixo) {
        if (zona == null) {
            throw new IllegalArgumentException("Zona é obrigatória.");
        }
        String prefixoEfetivo = prefixo == null ? "" : prefixo;
        return zonas.get(zona).keySet().stream().filter(caminho -> caminho.startsWith(prefixoEfetivo)).toList();
    }

    @Override
    public boolean existe(Zona zona, String caminho) {
        validar(zona, caminho);
        return zonas.get(zona).containsKey(caminho);
    }

    /** Apoio a testes: metadados gravados no objeto, ou {@code null} se o objeto não existe. */
    public Map<String, String> metadadosDe(Zona zona, String caminho) {
        Objeto objeto = zonas.get(zona).get(caminho);
        return objeto == null ? null : objeto.metadados();
    }

    private static void validar(Zona zona, String caminho) {
        if (zona == null) {
            throw new IllegalArgumentException("Zona é obrigatória.");
        }
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("Caminho do objeto é obrigatório.");
        }
    }
}