package br.com.fatec.georural.service.storage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilitário responsável por definir e compor de forma determinística
 * o caminho (endereço do objeto) e os metadados padrão associados a arquivos
 * armazenados na zona bruta do Data Lake (bucket {@code georural-bruta}).
 *
 * <p>Convenção de caminho definida para a US#07:
 * {@code conjuntos/{conId}/brutos/{ano}/{mes}/{hash}_{nomeOriginal}}
 *
 * <p>Convenção de metadados obrigatórios do objeto (OCI Object Storage):
 * <ul>
 *   <li>{@code hash-sha256}: Hash criptográfico SHA-256 do arquivo</li>
 *   <li>{@code con-id}: Identificador do conjunto de dados de destino</li>
 *   <li>{@code usu-id}: Identificador do usuário responsável provisório</li>
 * </ul>
 */
public final class CaminhoZonaBrutaHelper {

    public static final String PREFIXO_CONJUNTOS = "conjuntos";
    public static final String SUBDIRETORIO_BRUTOS = "brutos";

    public static final String META_HASH = "hash-sha256";
    public static final String META_CONJUNTO_ID = "con-id";
    public static final String META_USUARIO_ID = "usu-id";

    private CaminhoZonaBrutaHelper() {
        // Impede instanciação de classe utilitária
    }

    /**
     * Gera o caminho determinístico do objeto na zona bruta a partir da data/hora de recebimento.
     *
     * @param conId Identificador do conjunto de dados (obrigatório, positivo)
     * @param dataHoraRecebimento Data e hora do recebimento (obrigatório)
     * @param hash Hash SHA-256 do arquivo em hexadecimal (obrigatório)
     * @param nomeOriginal Nome original do arquivo (obrigatório)
     * @return Caminho completo relativo no bucket georural-bruta
     */
    public static String gerarCaminho(Long conId, LocalDateTime dataHoraRecebimento, String hash, String nomeOriginal) {
        if (dataHoraRecebimento == null) {
            throw new IllegalArgumentException("Data/hora de recebimento é obrigatória para compor o caminho na zona bruta.");
        }
        return gerarCaminho(conId, dataHoraRecebimento.getYear(), dataHoraRecebimento.getMonthValue(), hash, nomeOriginal);
    }

    /**
     * Gera o caminho determinístico do objeto na zona bruta a partir da data de recebimento.
     *
     * @param conId Identificador do conjunto de dados (obrigatório, positivo)
     * @param dataRecebimento Data do recebimento (obrigatório)
     * @param hash Hash SHA-256 do arquivo em hexadecimal (obrigatório)
     * @param nomeOriginal Nome original do arquivo (obrigatório)
     * @return Caminho completo relativo no bucket georural-bruta
     */
    public static String gerarCaminho(Long conId, LocalDate dataRecebimento, String hash, String nomeOriginal) {
        if (dataRecebimento == null) {
            throw new IllegalArgumentException("Data de recebimento é obrigatória para compor o caminho na zona bruta.");
        }
        return gerarCaminho(conId, dataRecebimento.getYear(), dataRecebimento.getMonthValue(), hash, nomeOriginal);
    }

    /**
     * Gera o caminho determinístico do objeto na zona bruta com ano e mês explícitos.
     *
     * @param conId Identificador do conjunto de dados (obrigatório, positivo)
     * @param ano Ano com 4 dígitos (ex.: 2026)
     * @param mes Mês entre 1 e 12
     * @param hash Hash SHA-256 do arquivo em hexadecimal (obrigatório)
     * @param nomeOriginal Nome original do arquivo (obrigatório)
     * @return Caminho completo no formato: {@code conjuntos/{conId}/brutos/{ano}/{mes}/{hash}_{nomeOriginal}}
     */
    public static String gerarCaminho(Long conId, int ano, int mes, String hash, String nomeOriginal) {
        if (conId == null || conId <= 0) {
            throw new IllegalArgumentException("Identificador do conjunto de dados (conId) deve ser informado e positivo.");
        }
        if (ano < 2000 || ano > 2100) {
            throw new IllegalArgumentException("Ano inválido para particionamento de caminho: " + ano);
        }
        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException("Mês inválido para particionamento de caminho: " + mes);
        }
        if (hash == null || !hash.trim().matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("Hash SHA-256 deve ter 64 caracteres hexadecimais.");
        }

        String nomeSanitizado = extrairNomeArquivo(nomeOriginal);
        String hashNormalizado = hash.trim().toLowerCase();

        return String.format("%s/%d/%s/%04d/%02d/%s_%s",
                PREFIXO_CONJUNTOS,
                conId,
                SUBDIRETORIO_BRUTOS,
                ano,
                mes,
                hashNormalizado,
                nomeSanitizado);
    }

    /**
     * Cria o mapa de metadados padrão do objeto a ser persistido no Object Storage (OCI {@code opcMeta}).
     *
     * @param hash Hash SHA-256 do arquivo (obrigatório)
     * @param conId Identificador do conjunto de dados (obrigatório)
     * @param usuId Identificador do usuário responsável provisório (obrigatório)
     * @return Mapa imutável contendo as chaves e valores de metadados
     */
    public static Map<String, String> criarMetadados(String hash, Long conId, Long usuId) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Hash SHA-256 é obrigatório para os metadados do objeto.");
        }
        if (conId == null || conId <= 0) {
            throw new IllegalArgumentException("Identificador do conjunto (conId) é obrigatório e deve ser positivo.");
        }
        if (usuId == null || usuId <= 0) {
            throw new IllegalArgumentException("Identificador do usuário responsável (usuId) é obrigatório e deve ser positivo.");
        }

        Map<String, String> metadados = new LinkedHashMap<>();
        metadados.put(META_HASH, hash.trim().toLowerCase());
        metadados.put(META_CONJUNTO_ID, String.valueOf(conId));
        metadados.put(META_USUARIO_ID, String.valueOf(usuId));

        return Collections.unmodifiableMap(metadados);
    }

    /**
     * Sanitiza o nome original do arquivo removendo caminhos absolutos ou relativos (prevenção de path traversal).
     *
     * @param nomeOriginal Nome do arquivo informado pelo cliente
     * @return Apenas o nome base do arquivo (ex.: "dados.zip")
     */
    public static String extrairNomeArquivo(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            throw new IllegalArgumentException("Nome original do arquivo é obrigatório.");
        }
        String nome = nomeOriginal.replace('\\', '/');
        int lastSlash = nome.lastIndexOf('/');
        if (lastSlash >= 0) {
            nome = nome.substring(lastSlash + 1);
        }
        nome = nome.trim();
        if (nome.isEmpty()) {
            throw new IllegalArgumentException("Nome original do arquivo inválido após sanitização: " + nomeOriginal);
        }
        if (nome.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Nome do arquivo contém caracteres de controle.");
        }
        if (nome.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 255) {
            throw new IllegalArgumentException("Nome do arquivo excede 255 bytes.");
        }
        return nome;
    }
}
