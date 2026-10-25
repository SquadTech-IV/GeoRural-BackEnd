package br.com.fatec.georural.service;

import br.com.fatec.georural.config.DataLakeProperties;
import br.com.fatec.georural.dto.response.DataLakeStatusResponse;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.HeadBucketRequest;
import com.oracle.bmc.retrier.RetryConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataLakeStatusService {

    private static final Logger log = LoggerFactory.getLogger(DataLakeStatusService.class);

    private final ObjectProvider<ObjectStorage> clienteProvider;
    private final DataLakeProperties props;

    public DataLakeStatusService(ObjectProvider<ObjectStorage> clienteProvider,
                                 DataLakeProperties props) {
        this.clienteProvider = clienteProvider;
        this.props = props;
    }

    public DataLakeStatusResponse verificar() {

        ObjectStorage cliente;
        try {
            cliente = clienteProvider.getObject();
        } catch (Exception | LinkageError e) {
            return falhou("Nao consegui montar o cliente da OCI (arquivo " + props.ociConfigFile()
                    + ", perfil " + props.ociProfile() + ") - " + causaRaiz(e), e);
        }

        String namespace;
        try {
            namespace = cliente.getNamespace(GetNamespaceRequest.builder()
                    .retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION)
                    .build()).getValue();
        } catch (Exception | LinkageError e) {
            return falhou(descrever(e), e);
        }
        boolean namespaceConfere = namespace.equals(props.namespace());

        Map<String, String> buckets = new LinkedHashMap<>();
        DataLakeProperties.Bucket b = props.bucket();
        for (String nome : List.of(b.bruta(), b.quarentena(), b.tratada(), b.publicada())) {
            buckets.put(nome, testarBucket(cliente, nome));
        }

        boolean bucketsOk = buckets.values().stream().allMatch("OK"::equals);
        boolean tudoOk = namespaceConfere && bucketsOk;
        if (!tudoOk) {
            log.warn("Data lake: namespace {} (confere: {}), buckets: {}",
                    namespace, namespaceConfere, buckets);
        }
        return new DataLakeStatusResponse(tudoOk, true, namespace, namespaceConfere, buckets, null);
    }

    private String testarBucket(ObjectStorage cliente, String bucket) {
        try {
            cliente.headBucket(HeadBucketRequest.builder()
                    .namespaceName(props.namespace())
                    .bucketName(bucket)
                    .retryConfiguration(RetryConfiguration.NO_RETRY_CONFIGURATION)
                    .build());
            return "OK";
        } catch (Exception | LinkageError e) {
            return descrever(e);
        }
    }

    private DataLakeStatusResponse falhou(String motivo, Throwable e) {
        log.warn("Data lake: {}", motivo, e);
        return new DataLakeStatusResponse(false, false, null, false, Map.of(), motivo);
    }

    private String descrever(Throwable e) {
        if (e instanceof BmcException bmc && !bmc.isClientSide() && !bmc.isTimeout()) {
            // A OCI respondeu, mas com erro (tem codigo HTTP)
            String erro = "ERRO " + bmc.getStatusCode()
                    + (bmc.getServiceCode() != null ? " " + bmc.getServiceCode() : "");
            return switch (bmc.getStatusCode()) {
                case 401 -> erro + " - a OCI recusou a chave: confira user, fingerprint e key_file"
                        + " no config, e se o relogio do PC esta certo";
                case 404 -> erro + " - o bucket nao existe ou este usuario nao tem permissao para ve-lo";
                default -> erro + " - " + bmc.getUnmodifiedMessage();
            };
        }

        return "ERRO sem resposta da OCI (confira internet/firewall e a chave .pem) - " + causaRaiz(e);
    }

    private static String causaRaiz(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String texto = raiz.getClass().getSimpleName() + ": " + raiz.getMessage();
        if (raiz instanceof LinkageError) {
            texto += " (conflito de versao de biblioteca: mande o log completo)";
        }
        return texto;
    }
}