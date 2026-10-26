package br.com.fatec.georural.dto.request.cadastro;

public record ConjuntoRequest(
    Long fonteId,
    String nome,
    String tipoCamada,
    String descricao,
    String esquemaTecnico,
    Integer srs,
    String competencia,
    String cobertura,
    String periodicidade,
    Boolean upload,
    Boolean api,
    String endpoint,
    String formatoApi,
    String autenticacao
) {
    public ConjuntoRequest(
        Long fonteId, String nome, String tipoCamada, String descricao,
        String esquemaTecnico, Integer srs, String competencia,
        String cobertura, String periodicidade
    ) {
        this(fonteId, nome, tipoCamada, descricao, esquemaTecnico, srs,
            competencia, cobertura, periodicidade, null, null, null, null, null);
    }
}
