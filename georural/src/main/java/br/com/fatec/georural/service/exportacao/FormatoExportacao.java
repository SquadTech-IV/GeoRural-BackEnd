package br.com.fatec.georural.service.exportacao;

public enum FormatoExportacao {

    CSV("text/csv; charset=UTF-8", "csv", false),
    JSON("application/json; charset=UTF-8", "json", false),
    GEOJSON("application/geo+json; charset=UTF-8", "geojson", true);

    private final String contentType;
    private final String extensao;
    private final boolean exigeGeometria;

    FormatoExportacao(String contentType, String extensao, boolean exigeGeometria) {
        this.contentType = contentType;
        this.extensao = extensao;
        this.exigeGeometria = exigeGeometria;
    }

    public String contentType() { return contentType; }
    public String extensao() { return extensao; }
    public boolean exigeGeometria() { return exigeGeometria; }

    public static FormatoExportacao de(String valor) {
        if (valor == null || valor.isBlank()) {
            return CSV;
        }
        for (FormatoExportacao f : values()) {
            if (f.name().equalsIgnoreCase(valor.trim())) {
                return f;
            }
        }
        throw new IllegalArgumentException(
                "Formato nao suportado: " + valor + ". Use CSV, JSON ou GEOJSON.");
    }
}
