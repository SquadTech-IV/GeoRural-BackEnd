package br.com.fatec.georural.service.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SHA256StreamCalculatorTest {

    // Valores de referência publicados do SHA-256.
    private static final String HASH_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String HASH_VAZIO = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @TempDir
    Path pasta;

    @Test
    void calcular_stream_deveRetornarHashConhecido() throws IOException {
        InputStream entrada = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));

        assertEquals(HASH_ABC, SHA256StreamCalculator.calcular(entrada));
    }

    @Test
    void calcular_conteudoVazio_deveRetornarHashDoVazio() throws IOException {
        assertEquals(HASH_VAZIO, SHA256StreamCalculator.calcular(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void calcular_arquivo_deveRetornarHashConhecido() throws IOException {
        Path arquivo = pasta.resolve("abc.txt");
        Files.write(arquivo, "abc".getBytes(StandardCharsets.UTF_8));

        assertEquals(HASH_ABC, SHA256StreamCalculator.calcular(arquivo));
    }

    @Test
    void calcular_deveRetornarHexadecimalMinusculoCom64Caracteres() throws IOException {
        String hash = SHA256StreamCalculator.calcular(new ByteArrayInputStream("qualquer conteúdo".getBytes(StandardCharsets.UTF_8)));

        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void calcular_conteudoMaiorQueOBuffer_deveCoincidirComCalculoDireto() throws Exception {
        byte[] dados = new byte[5 * 1024 * 1024 + 123];
        for (int i = 0; i < dados.length; i++) {
            dados[i] = (byte) (i * 31 + 7);
        }
        String esperado = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(dados));

        // Entrega em fatias pequenas para exercitar leituras parciais do stream.
        InputStream fatiado = new FilterInputStream(new ByteArrayInputStream(dados)) {
            @Override
            public int read(byte[] destino, int inicio, int tamanho) throws IOException {
                return super.read(destino, inicio, Math.min(tamanho, 1000));
            }
        };

        assertEquals(esperado, SHA256StreamCalculator.calcular(fatiado));
    }

    @Test
    void calcular_stream_naoDeveFecharOStreamRecebido() throws IOException {
        AtomicBoolean fechado = new AtomicBoolean(false);
        InputStream entrada = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
                fechado.set(true);
                super.close();
            }
        };

        SHA256StreamCalculator.calcular(entrada);

        assertFalse(fechado.get());
    }

    @Test
    void calcular_streamNulo_deveLancarIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> SHA256StreamCalculator.calcular((InputStream) null));
    }

    @Test
    void calcular_caminhoNulo_deveLancarIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> SHA256StreamCalculator.calcular((Path) null));
    }

    @Test
    void calcular_arquivoInexistente_deveLancarIOException() {
        assertThrows(IOException.class, () -> SHA256StreamCalculator.calcular(pasta.resolve("nao-existe.bin")));
    }

    @Test
    void algoritmo_deveSerSha256() {
        assertEquals("SHA-256", SHA256StreamCalculator.ALGORITMO);
    }
}