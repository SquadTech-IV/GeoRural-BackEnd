package br.com.fatec.georural.service.pipeline.perfil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// tipos aceitos nos campos de uma camada
public enum TipoCampo {

    TEXTO("texto"),
    INTEIRO("numero inteiro"),
    DECIMAL("numero decimal"),
    DATA("data");

    private static final List<DateTimeFormatter> FORMATOS_DATA = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    private final String descricao;

    TipoCampo(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }

    // converte o valor bruto do arquivo para o tipo do campo
    public Object converter(Object bruto) {
        if (bruto == null) {
            return null;
        }
        String texto = String.valueOf(bruto).trim();
        if (texto.isEmpty()) {
            return null;
        }
        return switch (this) {
            case TEXTO -> texto;
            case INTEIRO -> Long.parseLong(limparNumero(texto));
            case DECIMAL -> new BigDecimal(limparNumero(texto));
            case DATA -> converterData(texto);
        };
    }

    // numero brasileiro: 1.234,56 vira 1234.56
    private String limparNumero(String texto) {
        String limpo = texto.replace(" ", "");
        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(',', '.');
        }
        return limpo;
    }

    private LocalDate converterData(String texto) {
        String base = texto.length() > 10 ? texto.substring(0, 10) : texto;
        for (DateTimeFormatter formato : FORMATOS_DATA) {
            try {
                return LocalDate.parse(base, formato);
            } catch (RuntimeException ignorado) {
                // tenta o formato seguinte
            }
        }
        throw new IllegalArgumentException("data em formato nao reconhecido: " + texto);
    }

    public String exemploEsperado() {
        return switch (this) {
            case TEXTO -> "texto";
            case INTEIRO -> "numero inteiro (ex.: 4106902)";
            case DECIMAL -> "numero decimal (ex.: 125.47 ou 125,47)";
            case DATA -> "data (ex.: 2024-03-15 ou 15/03/2024)";
        };
    }

    public static TipoCampo de(String nome) {
        return valueOf(nome.toUpperCase(Locale.ROOT));
    }
}
