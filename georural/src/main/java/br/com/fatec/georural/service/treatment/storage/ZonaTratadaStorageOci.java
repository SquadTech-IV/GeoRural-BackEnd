package br.com.fatec.georural.service.treatment.storage;

import java.io.ByteArrayInputStream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;

import br.com.fatec.georural.config.DataLakeProperties;

/**
 * Grava a zona tratada no bucket "tratada" do OCI Object Storage.
 * Ative com georural.zona-tratada.storage=oci.
 */
@Component
@ConditionalOnProperty(name = "georural.zona-tratada.storage", havingValue = "oci")
public class ZonaTratadaStorageOci implements ZonaTratadaStorage {

    private final ObjectProvider<ObjectStorage> clienteProvider;

    private final DataLakeProperties props;

    public ZonaTratadaStorageOci(
            ObjectProvider<ObjectStorage> clienteProvider,
            DataLakeProperties props) {

        this.clienteProvider = clienteProvider;
        this.props = props;
    }

    @Override
    public String gravar(String chave, byte[] conteudo) {

        try {

            ObjectStorage cliente = clienteProvider.getObject();

            String bucket = props.bucket().tratada();

            cliente.putObject(PutObjectRequest.builder()
                    .namespaceName(props.namespace())
                    .bucketName(bucket)
                    .objectName(chave)
                    .contentType("application/x-ndjson")
                    .contentLength((long) conteudo.length)
                    .putObjectBody(new ByteArrayInputStream(conteudo))
                    .build());

            return "oci://%s/%s/%s".formatted(props.namespace(), bucket, chave);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Erro ao gravar na zona tratada (OCI): " + e.getMessage(),
                    e);
        }
    }
}
