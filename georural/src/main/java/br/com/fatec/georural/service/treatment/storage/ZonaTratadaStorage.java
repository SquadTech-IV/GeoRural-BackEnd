package br.com.fatec.georural.service.treatment.storage;

/**
 * Abstração do object storage da zona tratada. Troque a implementação
 * (disco, MinIO, S3...) sem mexer nos estados do pipeline.
 */
public interface ZonaTratadaStorage {

    /** Grava o conteúdo na chave informada e devolve o caminho/URI final. */
    String gravar(String chave, byte[] conteudo);
}
