package br.com.fatec.georural.dto.ingestao;

public record ConjuntoIngestaoResponse(
        Long id,
        String nome,
        String tipo,
        String formaEntrada,
        boolean aceitaApi,
        String apiEndpoint,
        String apiFormato,
        String periodicidade
) {
}