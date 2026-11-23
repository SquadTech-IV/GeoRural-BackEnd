package br.com.fatec.georural.config;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.HeadBucketRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {

    private final DataLakeProperties lakeProps;
    private final ObjectProvider<ObjectStorage> objectStorageProvider;

    @Value("${georural.zona-tratada.storage:oci}")
    private String zonaTratadaStorage;

    @Value("${georural.zona-tratada.base-dir:./zona-tratada}")
    private String zonaTratadaBaseDir;

    public StorageHealthIndicator(
            DataLakeProperties lakeProps,
            ObjectProvider<ObjectStorage> objectStorageProvider
    ) {
        this.lakeProps = lakeProps;
        this.objectStorageProvider = objectStorageProvider;
    }

    @Override
    public Health health() {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("zonaTratadaStorage", zonaTratadaStorage);
        detalhes.put("namespace", lakeProps.namespace());
        detalhes.put("region", lakeProps.region());

        if ("local".equalsIgnoreCase(zonaTratadaStorage)) {
            Path dir = Path.of(zonaTratadaBaseDir);
            detalhes.put("baseDir", dir.toAbsolutePath().toString());
            if (Files.exists(dir) && Files.isDirectory(dir) && Files.isWritable(dir)) {
                return Health.up().withDetails(detalhes).build();
            }
            return Health.down()
                    .withDetails(detalhes)
                    .withDetail("motivo", "Diretorio local indisponivel ou sem permissao de escrita")
                    .build();
        }

        String bucketBruta = lakeProps.bucket() != null ? lakeProps.bucket().bruta() : null;
        detalhes.put("bucketBruta", bucketBruta);

        try {
            ObjectStorage objectStorage = objectStorageProvider.getIfAvailable();
            if (objectStorage == null) {
                return Health.down()
                        .withDetails(detalhes)
                        .withDetail("erro", "Bean ObjectStorage indisponivel")
                        .build();
            }

            HeadBucketRequest request = HeadBucketRequest.builder()
                    .namespaceName(lakeProps.namespace())
                    .bucketName(bucketBruta)
                    .build();

            objectStorage.headBucket(request);
            return Health.up().withDetails(detalhes).build();
        } catch (Exception ex) {
            return Health.down()
                    .withDetails(detalhes)
                    .withDetail("erro", ex.getMessage())
                    .build();
        }
    }
}