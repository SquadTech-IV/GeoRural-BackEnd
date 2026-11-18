package br.com.fatec.georural.service.exportacao;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class EscritorGeoJson implements EscritorExportacao {

    private final Writer out;
    private boolean primeira = true;

    private String carAtual;
    private LinhaResultado cabecaDoImovel;
    private final Map<String, LinhaResultado> indicadoresDoImovel = new LinkedHashMap<>();

    public EscritorGeoJson(Writer out) {
        this.out = out;
    }

    @Override
    public void inicio() throws IOException {
        out.write("{\"type\":\"FeatureCollection\",\"features\":[");
    }

    @Override
    public void linha(LinhaResultado l) throws IOException {
        if (carAtual != null && !carAtual.equals(l.codigoCar())) {
            despejar();
        }
        carAtual = l.codigoCar();
        if (cabecaDoImovel == null) {
            cabecaDoImovel = l;
        }
        indicadoresDoImovel.put(l.indicador(), l);
    }

    @Override
    public void fim() throws IOException {
        despejar();
        out.write("]}");
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }

    private void despejar() throws IOException {
        if (cabecaDoImovel == null) {
            return;
        }
        LinhaResultado c = cabecaDoImovel;
        if (!primeira) out.write(",");
        primeira = false;

        out.write("\n{\"type\":\"Feature\",\"geometry\":");
        out.write(c.geometria() == null || c.geometria().isBlank() ? "null" : c.geometria());
        out.write(",\"properties\":{");

        texto("codigo_car", c.codigoCar(), true);
        texto("municipio", c.municipio(), false);
        texto("codigo_ibge", c.codigoIbge(), false);
        numero("area_total_ha", c.areaTotalHa());

        for (Map.Entry<String, LinhaResultado> e : indicadoresDoImovel.entrySet()) {
            String sigla = e.getKey();
            LinhaResultado l = e.getValue();
            numero(sigla + "_valor", l.valorPercentual() != null ? l.valorPercentual() : l.valorAbsoluto());
            numero(sigla + "_ha", l.valorHectares());
            texto(sigla + "_situacao", l.situacao(), false);
        }

        out.write("}}");

        cabecaDoImovel = null;
        indicadoresDoImovel.clear();
    }

    private void texto(String chave, String valor, boolean primeiroCampo) throws IOException {
        if (!primeiroCampo) out.write(",");
        out.write("\"" + chave + "\":");
        out.write(valor == null ? "null" : "\"" + EscritorJson.escapar(valor) + "\"");
    }

    private void numero(String chave, BigDecimal valor) throws IOException {
        out.write(",\"" + chave + "\":");
        out.write(valor == null ? "null" : valor.stripTrailingZeros().toPlainString());
    }
}
