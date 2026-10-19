package br.com.fatec.georural.service.storage;

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.HeadObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.HeadObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;

import java.io.InputStream;
import java.util.Map;

/**
 * Política de imutabilidade da zona bruta do Data Lake (bucket {@code georural-bruta}).
 *
 * <p>Regras aplicadas por esta classe:
 * <ul>
 *   <li>Todo envio usa {@code If-None-Match: *}, impedindo a sobrescrita de objetos já existentes.</li>
 *   <li>Nenhum método de apagar ou alterar objetos é exposto; a zona bruta é somente-criação.</li>
 *   <li>A regra de retenção do bucket e as permissões IAM de bloqueio de deleção são configuradas
 *       fora do código (ver Pendências da T002 em tasks.md).</li>
 * </ul>
 *
 * <p>Uso esperado: esta classe é utilizada pela camada de serviço ({@code ArmazenamentoZonaBrutaService})
 * para construir e executar as operações permitidas na zona bruta.
 *
 * @see CaminhoZonaBrutaHelper
 */
public final class PoliticaZonaBruta {

    /**
     * Valor do cabeçalho {@code If-None-Match} utilizado em todo PUT na zona bruta.
     * O asterisco ({@code *}) instrui o OCI Object Storage a rejeitar o envio caso
     * o objeto já exista, garantindo que nenhum objeto seja sobrescrito.
     */
    public static final String IF_NONE_MATCH_QUALQUER = "*";

    /**
     * Código HTTP retornado pelo OCI Object Storage quando o objeto já existe
     * e o cabeçalho {@code If-None-Match: *} foi enviado.
     */
    public static final int STATUS_PRECONDITION_FAILED = 412;

    /** Código HTTP retornado pelo HEAD quando o objeto não é encontrado. */
    public static final int STATUS_NOT_FOUND = 404;

    private PoliticaZonaBruta() {
        // Impede instanciação de classe utilitária de política
    }

    /**
     * Envia um objeto para a zona bruta aplicando a política de imutabilidade.
     *
     * <p>O cabeçalho {@code If-None-Match: *} é incluído automaticamente: se o objeto já existir,
     * o OCI Object Storage retorna HTTP 412 (Precondition Failed) e a operação é rejeitada —
     * nunca sobreescrevendo o objeto existente.
     *
     * @param cliente     Cliente OCI Object Storage já configurado
     * @param namespace   Namespace OCI do tenancy
     * @param bucket      Nome do bucket da zona bruta (ex.: {@code georural-bruta})
     * @param caminho     Caminho do objeto conforme {@link CaminhoZonaBrutaHelper#gerarCaminho}
     * @param conteudo    Stream com o conteúdo do arquivo (não pode ser {@code null})
     * @param tamanho     Tamanho do conteúdo em bytes
     * @param metadados   Metadados obrigatórios do objeto (hash, conId, usuId); pode ser {@code null}
     * @return Resposta do OCI Object Storage para a operação PUT
     * @throws com.oracle.bmc.model.BmcException com HTTP 412 se o objeto já existir no bucket
     * @throws IllegalArgumentException se algum argumento obrigatório for nulo ou em branco
     */
    public static PutObjectResponse enviarObjeto(
            ObjectStorage cliente,
            String namespace,
            String bucket,
            String caminho,
            InputStream conteudo,
            long tamanho,
            Map<String, String> metadados) {

        validarArgumentosEnvio(cliente, namespace, bucket, caminho, conteudo);

        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucket)
                .objectName(caminho)
                .contentLength(tamanho)
                .putObjectBody(conteudo)
                .ifNoneMatch(IF_NONE_MATCH_QUALQUER);

        if (metadados != null && !metadados.isEmpty()) {
            builder.opcMeta(metadados);
        }

        return cliente.putObject(builder.build());
    }

    /**
     * Verifica se um objeto existe na zona bruta sem baixar seu conteúdo (requisição HEAD).
     *
     * @param cliente   Cliente OCI Object Storage já configurado
     * @param namespace Namespace OCI do tenancy
     * @param bucket    Nome do bucket da zona bruta
     * @param caminho   Caminho do objeto a verificar
     * @return {@code true} se o objeto existe; {@code false} se não encontrado (HTTP 404)
     * @throws com.oracle.bmc.model.BmcException para erros diferentes de 404
     */
    public static boolean objetoExiste(
            ObjectStorage cliente,
            String namespace,
            String bucket,
            String caminho) {

        validarArgumentosConsulta(cliente, namespace, bucket, caminho);

        HeadObjectRequest requisicao = HeadObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucket)
                .objectName(caminho)
                .build();

        try {
            HeadObjectResponse resposta = cliente.headObject(requisicao);
            return resposta != null;
        } catch (BmcException e) {
            if (e.getStatusCode() == STATUS_NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Indica se uma {@link BmcException} representa a rejeição por objeto já existente
     * (HTTP 412 Precondition Failed em resposta ao {@code If-None-Match: *}).
     *
     * @param excecao Exceção lançada pela operação de envio
     * @return {@code true} se o objeto já existe no bucket
     */
    public static boolean ehConflitoPorObjetoJaExistente(BmcException excecao) {
        return excecao != null && excecao.getStatusCode() == STATUS_PRECONDITION_FAILED;
    }

    // -------------------------------------------------------------------------
    // Validações internas
    // -------------------------------------------------------------------------

    private static void validarArgumentosEnvio(
            ObjectStorage cliente,
            String namespace,
            String bucket,
            String caminho,
            InputStream conteudo) {

        if (cliente == null) {
            throw new IllegalArgumentException("Cliente OCI Object Storage é obrigatório.");
        }
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Namespace OCI é obrigatório.");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Nome do bucket é obrigatório.");
        }
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("Caminho do objeto é obrigatório.");
        }
        if (conteudo == null) {
            throw new IllegalArgumentException("Stream de conteúdo é obrigatório.");
        }
    }

    private static void validarArgumentosConsulta(
            ObjectStorage cliente,
            String namespace,
            String bucket,
            String caminho) {

        if (cliente == null) {
            throw new IllegalArgumentException("Cliente OCI Object Storage é obrigatório.");
        }
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("Namespace OCI é obrigatório.");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Nome do bucket é obrigatório.");
        }
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("Caminho do objeto é obrigatório.");
        }
    }
}