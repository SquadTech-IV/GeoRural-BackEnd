package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.config.DataLakeProperties;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;

/**
 * Acesso mínimo à zona bruta (bucket {@code georural-bruta}): gravar sem sobrescrever, verificar
 * existência e ler. Não existe método de apagar nem de alterar objeto.
 *
 * <p>Na T011 uma interface é extraída desta classe.
 */
@Service
public class ArmazenamentoZonaBrutaService {

    private final ObjectStorage cliente;
    private final String namespace;
    private final String bucket;

    // @Lazy preserva a criação tardia do cliente OCI definida em DataLakeConfig.
    public ArmazenamentoZonaBrutaService(@Lazy ObjectStorage cliente, DataLakeProperties propriedades) {
        this.cliente = cliente;
        this.namespace = propriedades.namespace();
        this.bucket = propriedades.bucket().bruta();
    }

    /**
     * Grava o objeto com {@code If-None-Match: *}. Se o objeto já existir, a OCI responde HTTP 412
     * (ver {@link PoliticaZonaBruta#ehConflitoPorObjetoJaExistente}).
     */
    public PutObjectResponse armazenarObjetoBruto(String caminho, InputStream conteudo, long tamanho,
                                                  Map<String, String> metadados) {
        return PoliticaZonaBruta.enviarObjeto(cliente, namespace, bucket, caminho, conteudo, tamanho, metadados);
    }

    public boolean verificarExistenciaObjeto(String caminho) {
        return PoliticaZonaBruta.objetoExiste(cliente, namespace, bucket, caminho);
    }

    /** Devolve o stream do objeto. Quem chama deve fechá-lo. */
    public InputStream obterObjetoBruto(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("Caminho do objeto é obrigatório.");
        }
        GetObjectRequest requisicao = GetObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucket)
                .objectName(caminho)
                .build();
        return cliente.getObject(requisicao).getInputStream();
    }
}