package br.com.fatec.georural.dto.ingestao;

public enum FormatoRespostaApi {

    GEOJSON("application/geo+json", "geojson"),
    JSON("application/json", "json"),
    CSV("text/csv", "csv"),
    WFS_GML("application/gml+xml", "gml"),
    KML("application/vnd.google-earth.kml+xml", "kml");

    private final String contentType;
    private final String extensao;

    FormatoRespostaApi(String contentType, String extensao) {
        this.contentType = contentType;
        this.extensao = extensao;
    }

    public String contentType() { return contentType; }
    public String extensao() { return extensao; }

    /** Converte o texto livre do cadastro para o enum. */
    public static FormatoRespostaApi doCadastro(String valor) {
        if (valor == null || valor.isBlank()) return JSON;
        String v = valor.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        return switch (v) {
            case "GEOJSON" -> GEOJSON;
            case "CSV" -> CSV;
            case "WFS", "GML", "WFS_GML" -> WFS_GML;
            case "KML" -> KML;
            default -> JSON;
        };
    }
}