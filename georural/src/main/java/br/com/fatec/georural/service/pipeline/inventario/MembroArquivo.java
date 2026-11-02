package br.com.fatec.georural.service.pipeline.inventario;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

// um conjunto de dados encontrado dentro de um arquivo container
public record MembroArquivo(
        String caminhoInterno,
        String nome,
        FormatoArquivo formato,
        String hash,
        long tamanhoBytes,
        Integer srsEpsg,
        List<String> partes,
        String observacao,
        boolean aberto) {

    // um conjunto de dados de verdade, lido do pacote
    public static MembroArquivo lido(String caminhoInterno, String nome, FormatoArquivo formato,
                                     String hash, long tamanhoBytes, Integer srsEpsg,
                                     List<String> partes, String observacao) {
        return new MembroArquivo(caminhoInterno, nome, formato, hash, tamanhoBytes, srsEpsg,
                partes, observacao, true);
    }

    // pacote que o inventario NAO conseguiu abrir
    public static MembroArquivo naoAberto(String caminhoInterno, String nome, String observacao) {
        return new MembroArquivo(caminhoInterno, nome, FormatoArquivo.ZIP,
                hashDoCaminho(caminhoInterno), 0L, null, List.of(), observacao, false);
    }

    public boolean temVariasPartes() {
        return partes != null && partes.size() > 1;
    }

    private static String hashDoCaminho(String caminho) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(("caminho:" + caminho).getBytes(StandardCharsets.UTF_8));
            StringBuilder texto = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                texto.append(String.format("%02x", b));
            }
            return texto.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM.", e);
        }
    }
}
