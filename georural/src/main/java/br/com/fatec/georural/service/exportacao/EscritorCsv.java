package br.com.fatec.georural.service.exportacao;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;


public class EscritorCsv implements EscritorExportacao {

    private static final String BOM = "﻿";
    private static final String FIM_DE_LINHA = "\r\n";

    private static final String[] CABECALHO = {
            "codigo_car", "municipio", "codigo_ibge", "area_total_ha",
            "indicador", "unidade", "valor_percentual", "valor_hectares",
            "valor_absoluto", "situacao", "data_calculo"
    };

    private final Writer out;
    private final SeparadorCsv separador;
    private final boolean comGeometria;

    public EscritorCsv(Writer out, SeparadorCsv separador, boolean comGeometria) {
        this.out = out;
        this.separador = separador;
        this.comGeometria = comGeometria;
    }

    @Override
    public void inicio() throws IOException {
        out.write(BOM);
        for (int i = 0; i < CABECALHO.length; i++) {
            if (i > 0) out.write(separador.campo());
            out.write(CABECALHO[i]);
        }
        if (comGeometria) {
            out.write(separador.campo());
            out.write("geometria");
        }
        out.write(FIM_DE_LINHA);
    }

    @Override
    public void linha(LinhaResultado l) throws IOException {
        campo(l.codigoCar(), true);
        campo(l.municipio(), false);
        campo(l.codigoIbge(), false);
        campo(numero(l.areaTotalHa()), false);
        campo(l.indicador(), false);
        campo(l.unidade(), false);
        campo(numero(l.valorPercentual()), false);
        campo(numero(l.valorHectares()), false);
        campo(numero(l.valorAbsoluto()), false);
        campo(l.situacao(), false);
        campo(l.dataCalculo() == null ? "" : l.dataCalculo().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), false);
        if (comGeometria) {
            campo(l.geometria(), false);
        }
        out.write(FIM_DE_LINHA);
    }

    @Override
    public void fim() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }

    private void campo(String valor, boolean primeiro) throws IOException {
        if (!primeiro) out.write(separador.campo());
        out.write(escapar(valor == null ? "" : valor));
    }

    private String numero(BigDecimal valor) {
        if (valor == null) return "";
        String texto = valor.stripTrailingZeros().toPlainString();
        return separador.decimal() == ',' ? texto.replace('.', ',') : texto;
    }

    private String escapar(String valor) {
        boolean precisa = valor.indexOf(separador.campo()) >= 0
                || valor.indexOf('"') >= 0
                || valor.indexOf('\n') >= 0
                || valor.indexOf('\r') >= 0;
        if (!precisa) return valor;
        return '"' + valor.replace("\"", "\"\"") + '"';
    }
}
