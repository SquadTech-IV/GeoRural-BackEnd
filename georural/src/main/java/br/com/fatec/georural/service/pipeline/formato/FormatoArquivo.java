package br.com.fatec.georural.service.pipeline.formato;

import java.util.Set;


public enum FormatoArquivo {

    SHAPEFILE_ZIP("Shapefile (.zip)", true,  true),
    SHAPEFILE    ("Shapefile (.shp)", true,  false),
    GEOPACKAGE   ("GeoPackage",       true,  false),
    GEOJSON      ("GeoJSON",          true,  false),
    JSON         ("JSON",             true,  false),
    CSV          ("CSV",              true,  false),
    GEOTIFF      ("GeoTIFF",          false, false),
    ZIP          ("Arquivo compactado", false, true),
    DESCONHECIDO ("Desconhecido",     false, false);

    /** Extensoes que identificam o arquivo principal de um conjunto de dados. */
    public static final Set<String> EXTENSOES_PRINCIPAIS =
            Set.of("shp", "gpkg", "geojson", "json", "csv", "tif", "tiff", "zip");

    /** Partes que acompanham um shapefile e nao sao conjuntos por si so. */
    public static final Set<String> EXTENSOES_AUXILIARES_SHAPEFILE =
            Set.of("shx", "dbf", "prj", "cpg", "sbn", "sbx", "qix", "fix", "xml");

    private final String rotulo;
    private final boolean vetorial;
    private final boolean container;

    FormatoArquivo(String rotulo, boolean vetorial, boolean container) {
        this.rotulo = rotulo;
        this.vetorial = vetorial;
        this.container = container;
    }

    public String rotulo() {
        return rotulo;
    }

    /** Vetorial = da para calcular indicador direto. GeoTIFF e raster e precisa de
     *  estatistica zonal */
    public boolean vetorial() {
        return vetorial;
    }

    /** Pode conter outros arquivos dentro. */
    public boolean container() {
        return container;
    }

    public boolean suportado() {
        return this != DESCONHECIDO;
    }
}
