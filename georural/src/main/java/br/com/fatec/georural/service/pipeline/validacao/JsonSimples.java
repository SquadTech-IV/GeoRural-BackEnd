package br.com.fatec.georural.service.pipeline.validacao;

import java.util.Map;

// escreve um mapa simples como JSON, para os CLOB que o banco valida com IS JSON
public final class JsonSimples {

    public static String deMapa(Map<String, ?> mapa) {
        if (mapa == null || mapa.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean primeiro = true;
        for (Map.Entry<String, ?> campo : mapa.entrySet()) {
            if (campo.getKey() == null) {
                continue;
            }
            if (!primeiro) {
                json.append(',');
            }
            primeiro = false;
            json.append(texto(campo.getKey())).append(':').append(valor(campo.getValue()));
        }
        return json.append('}').toString();
    }

    private static String valor(Object valor) {
        if (valor == null) {
            return "null";
        }
        if (valor instanceof Number || valor instanceof Boolean) {
            return valor.toString();
        }
        return texto(String.valueOf(valor));
    }

    private static String texto(String bruto) {
        StringBuilder saida = new StringBuilder("\"");
        for (int i = 0; i < bruto.length(); i++) {
            char c = bruto.charAt(i);
            switch (c) {
                case '"' -> saida.append("\\\"");
                case '\\' -> saida.append("\\\\");
                case '\n' -> saida.append("\\n");
                case '\r' -> saida.append("\\r");
                case '\t' -> saida.append("\\t");
                default -> {
                    if (c < 0x20) {
                        saida.append(String.format("\\u%04x", (int) c));
                    } else {
                        saida.append(c);
                    }
                }
            }
        }
        return saida.append('"').toString();
    }

    private JsonSimples() {
    }
}
