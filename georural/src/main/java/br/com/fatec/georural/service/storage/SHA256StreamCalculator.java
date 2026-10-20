package br.com.fatec.georural.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Calcula o SHA-256 de um conteúdo lendo em fluxo, sem carregar o arquivo inteiro na memória.
 *
 * <p>O resultado é sempre hexadecimal minúsculo com 64 caracteres, o formato gravado em
 * {@code arq_hash} e usado na convenção de caminho da zona bruta.
 */
public final class SHA256StreamCalculator {

    /** Nome do algoritmo, gravado em {@code arq_algoritmo_hash}. */
    public static final String ALGORITMO = "SHA-256";

    private static final int TAMANHO_BUFFER = 64 * 1024;

    private SHA256StreamCalculator() {
    }

    /**
     * Calcula o hash do conteúdo do stream. O stream NÃO é fechado: quem o abriu é responsável por fechá-lo.
     */
    public static String calcular(InputStream entrada) throws IOException {
        if (entrada == null) {
            throw new IllegalArgumentException("Stream de entrada é obrigatório.");
        }
        MessageDigest digest = novoDigest();
        byte[] buffer = new byte[TAMANHO_BUFFER];
        int lidos;
        while ((lidos = entrada.read(buffer)) != -1) {
            digest.update(buffer, 0, lidos);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Calcula o hash do arquivo, abrindo e fechando o stream internamente.
     */
    public static String calcular(Path arquivo) throws IOException {
        if (arquivo == null) {
            throw new IllegalArgumentException("Caminho do arquivo é obrigatório.");
        }
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            return calcular(entrada);
        }
    }

    private static MessageDigest novoDigest() {
        try {
            return MessageDigest.getInstance(ALGORITMO);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo " + ALGORITMO + " indisponível na JVM.", e);
        }
    }
}