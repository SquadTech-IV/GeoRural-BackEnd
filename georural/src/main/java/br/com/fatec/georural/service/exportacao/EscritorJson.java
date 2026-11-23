package br.com.fatec.georural.service.exportacao;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;


public class EscritorJson implements EscritorExportacao {

    private final Writer out;
    private final boolean comGeometria;
    private boolean primeira = true;

    public EscritorJson(Writer out, boolean comGeometria) {
        this.out = out;
        this.comGeometria = comGeometria;
    }

    @Override
    public void inicio() throws IOException {
        out.write("[");
    }

    @Override
    public void linha(LinhaResultado l) throws IOException {
        if (!primeira) out.write(",");
        primeira = false;
        out.write("\n  {");
        texto("codigoCar", l.codigoCar(), true);
        texto("municipio", l.municipio(), false);
        texto("codigoIbge", l.codigoIbge(), false);
        numero("areaTotalHa", l.areaTotalHa());
        texto("indicador", l.indicador(), false);
        texto("unidade", l.unidade(), false);
        numero("valorPercentual", l.valorPercentual());
        numero("valorHectares", l.valorHectares());
        numero("valorAbsoluto", l.valorAbsoluto());
        texto("situacao", l.situacao(), false);
        texto("dataCalculo", l.dataCalculo() == null ? null
                : l.dataCalculo().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), false);
        cru("detalhes", l.detalhes());
        if (comGeometria) {
            cru("geometria", l.geometria());
        }
        out.write("}");
    }

    @Override
    public void fim() throws IOException {
        out.write(primeira ? "]" : "\n]");
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }

    private void texto(String chave, String valor, boolean primeiroCampo) throws IOException {
        if (!primeiroCampo) out.write(",");
        out.write("\"" + chave + "\":");
        out.write(valor == null ? "null" : "\"" + escapar(valor) + "\"");
    }

    private void numero(String chave, BigDecimal valor) throws IOException {
        out.write(",\"" + chave + "\":");
        out.write(valor == null ? "null" : valor.stripTrailingZeros().toPlainString());
    }

    private void cru(String chave, String json) throws IOException {
        out.write(",\"" + chave + "\":");
        out.write(json == null || json.isBlank() ? "null" : json);
    }

    static String escapar(String valor) {
        StringBuilder sb = new StringBuilder(valor.length() + 8);
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
