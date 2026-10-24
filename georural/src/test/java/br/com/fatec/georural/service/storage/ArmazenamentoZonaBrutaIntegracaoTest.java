package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.config.DataLakeConfig;
import br.com.fatec.georural.config.DataLakeProperties;
import br.com.fatec.georural.exception.ObjetoJaExisteException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de integração contra o armazenamento de objetos REAL (datalake de desenvolvimento).
 *
 * <p>ATENÇÃO: cada execução grava um objeto PERMANENTE no bucket, porque a zona bruta é imutável e o
 * backend não apaga. Por isso o teste só roda quando {@code GEORURAL_IT_DATALAKE=true} e usa conteúdo
 * aleatório a cada execução, sob o prefixo {@code testes-integracao/us07/}.
 *
 * <p>Variáveis de ambiente:
 * <ul>
 *   <li>{@code GEORURAL_IT_DATALAKE=true} (obrigatória, liga o teste)</li>
 *   <li>{@code GEORURAL_IT_NAMESPACE} (obrigatória, namespace da OCI)</li>
 *   <li>{@code GEORURAL_IT_BUCKET} (padrão: georural-bruta)</li>
 *   <li>{@code GEORURAL_IT_REGION} (padrão: sa-saopaulo-1)</li>
 *   <li>{@code GEORURAL_IT_OCI_PROFILE} (padrão: GEORURAL_APP)</li>
 *   <li>{@code GEORURAL_IT_OCI_CONFIG} (padrão: ~/.oci/config)</li>
 * </ul>
 *
 * <p>Cobre: envio, existência, leitura com recálculo do hash e bloqueio de sobrescrita. Não cobre o
 * registro no Oracle (validado pelo endpoint) nem as permissões de IAM do usuário da aplicação.
 */
@EnabledIfEnvironmentVariable(named = "GEORURAL_IT_DATALAKE", matches = "true")
class ArmazenamentoZonaBrutaIntegracaoTest {

    private static ObjectStorage cliente;
    private static OCIArmazenamentoZona armazenamento;

    @BeforeAll
    static void conectar() throws IOException {
        String namespace = System.getenv("GEORURAL_IT_NAMESPACE");
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalStateException("Defina GEORURAL_IT_NAMESPACE para executar o teste de integração.");
        }
        String bucket = System.getenv().getOrDefault("GEORURAL_IT_BUCKET", "georural-bruta");
        String regiao = System.getenv().getOrDefault("GEORURAL_IT_REGION", "sa-saopaulo-1");
        String perfil = System.getenv().getOrDefault("GEORURAL_IT_OCI_PROFILE", "GEORURAL_APP");
        String arquivoConfig = System.getenv().getOrDefault(
                "GEORURAL_IT_OCI_CONFIG", System.getProperty("user.home") + "/.oci/config");

        DataLakeProperties propriedades = new DataLakeProperties(
                regiao,
                namespace,
                new DataLakeProperties.Bucket(bucket, null, null, null),
                arquivoConfig,
                perfil);

        cliente = new DataLakeConfig().objectStorage(propriedades);
        armazenamento = new OCIArmazenamentoZona(cliente, propriedades);
    }

    @AfterAll
    static void fechar() {
        try {
            if (cliente != null) {
                cliente.close();
            }
        } catch (Exception ignorada) {
            // Falha ao fechar o cliente não invalida o resultado do teste.
        }
    }

    @Test
    void enviar_deveGravarSemAlterarERecalcularOMesmoHashEBloquearASobrescrita() throws Exception {
        String unico = UUID.randomUUID().toString();
        byte[] original = ("US07-integracao-" + unico + "\n").getBytes(StandardCharsets.UTF_8);
        String hash = SHA256StreamCalculator.calcular(new ByteArrayInputStream(original));
        String caminho = "testes-integracao/us07/" + unico + ".csv";

        // 1. O objeto ainda não existe.
        assertFalse(armazenamento.existe(Zona.BRUTA, caminho));

        // 2. Grava e confirma a existência.
        armazenamento.gravar(Zona.BRUTA,
                caminho, new ByteArrayInputStream(original), original.length, Map.of("hash-sha256", hash));
        assertTrue(armazenamento.existe(Zona.BRUTA, caminho));

        // 3. Relê o objeto armazenado e recalcula o hash: tem de ser o mesmo valor.
        try (InputStream lido = armazenamento.ler(Zona.BRUTA, caminho)) {
            assertEquals(hash, SHA256StreamCalculator.calcular(lido));
        }

        // 4. Uma segunda gravação no mesmo caminho é recusada (ObjetoJaExisteException) e não altera o objeto.
        byte[] diferente = ("OUTRO-CONTEUDO-" + unico + "\n").getBytes(StandardCharsets.UTF_8);
        assertThrows(ObjetoJaExisteException.class, () -> armazenamento.gravar(Zona.BRUTA,
                caminho, new ByteArrayInputStream(diferente), diferente.length, null));

        try (InputStream lido = armazenamento.ler(Zona.BRUTA, caminho)) {
            assertEquals(hash, SHA256StreamCalculator.calcular(lido));
        }
    }
}