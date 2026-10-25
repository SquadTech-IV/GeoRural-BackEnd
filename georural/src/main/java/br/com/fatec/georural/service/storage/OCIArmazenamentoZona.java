package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.config.DataLakeProperties;
import br.com.fatec.georural.exception.ObjetoJaExisteException;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementação da {@link ArmazenamentoZonaPort} sobre o OCI Object Storage.
 *
 * <p>Toda gravação usa {@code If-None-Match: *} (via {@link PoliticaZonaBruta}), em qualquer zona, e o
 * HTTP 412 da OCI é traduzido para {@link ObjetoJaExisteException}. Não há operação de apagar nem de alterar.
 */
@Service
public class OCIArmazenamentoZona implements ArmazenamentoZonaPort {

    private final ObjectStorage cliente;
    private final DataLakeProperties propriedades;

    // @Lazy preserva a criação tardia do cliente OCI definida em DataLakeConfig.
    public OCIArmazenamentoZona(@Lazy ObjectStorage cliente, DataLakeProperties propriedades) {
        this.cliente = cliente;
        this.propriedades = propriedades;
    }

    @Override
    public void gravar(Zona zona, String caminho, InputStream conteudo, long tamanho, Map<String, String> metadados) {
        String bucket = bucket(zona);
        try {
            PoliticaZonaBruta.enviarObjeto(cliente, propriedades.namespace(), bucket, caminho, conteudo, tamanho, metadados);
        } catch (BmcException e) {
            if (PoliticaZonaBruta.ehConflitoPorObjetoJaExistente(e)) {
                throw new ObjetoJaExisteException(caminho, e);
            }
            throw e;
        }
    }

    @Override
    public InputStream ler(Zona zona, String caminho) {
        String bucket = bucket(zona);
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("Caminho do objeto é obrigatório.");
        }
        GetObjectRequest requisicao = GetObjectRequest.builder()
                .namespaceName(propriedades.namespace())
                .bucketName(bucket)
                .objectName(caminho)
                .build();
        return cliente.getObject(requisicao).getInputStream();
    }

    @Override
    public List<String> listar(Zona zona, String prefixo) {
        String bucket = bucket(zona);
        String prefixoEfetivo = (prefixo == null || prefixo.isBlank()) ? null : prefixo;

        List<String> caminhos = new ArrayList<>();
        String proximo = null;
        do {
            ListObjectsRequest.Builder requisicao = ListObjectsRequest.builder()
                    .namespaceName(propriedades.namespace())
                    .bucketName(bucket)
                    .prefix(prefixoEfetivo);
            if (proximo != null) {
                requisicao.start(proximo);
            }
            ListObjectsResponse resposta = cliente.listObjects(requisicao.build());
            for (ObjectSummary objeto : resposta.getListObjects().getObjects()) {
                caminhos.add(objeto.getName());
            }
            proximo = resposta.getListObjects().getNextStartWith();
        } while (proximo != null);
        return caminhos;
    }

    @Override
    public boolean existe(Zona zona, String caminho) {
        return PoliticaZonaBruta.objetoExiste(cliente, propriedades.namespace(), bucket(zona), caminho);
    }

    private String bucket(Zona zona) {
        if (zona == null) {
            throw new IllegalArgumentException("Zona é obrigatória.");
        }
        DataLakeProperties.Bucket buckets = propriedades.bucket();
        String nome = switch (zona) {
            case BRUTA -> buckets.bruta();
            case QUARENTENA -> buckets.quarentena();
            case TRATADA -> buckets.tratada();
            case PUBLICADA -> buckets.publicada();
        };
        if (nome == null || nome.isBlank()) {
            throw new IllegalStateException("Bucket da zona " + zona + " não está configurado.");
        }
        return nome;
    }
}