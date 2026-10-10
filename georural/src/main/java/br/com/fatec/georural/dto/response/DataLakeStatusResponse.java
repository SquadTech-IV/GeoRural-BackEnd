package br.com.fatec.georural.dto.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;

@JsonPropertyOrder({"tudoOk", "conectado", "namespace", "namespaceConfere", "buckets", "erro"})
public record DataLakeStatusResponse(
        boolean tudoOk,
        boolean conectado,
        String namespace,
        boolean namespaceConfere,
        Map<String, String> buckets,
        String erro
) {}