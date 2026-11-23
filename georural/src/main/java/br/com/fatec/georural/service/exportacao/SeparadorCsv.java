package br.com.fatec.georural.service.exportacao;

public enum SeparadorCsv {

    PONTO_E_VIRGULA(';', ','),
    VIRGULA(',', '.');

    private final char campo;
    private final char decimal;

    SeparadorCsv(char campo, char decimal) {
        this.campo = campo;
        this.decimal = decimal;
    }

    public char campo() { return campo; }
    public char decimal() { return decimal; }

    public static SeparadorCsv de(String valor) {
        if (valor == null || valor.isBlank()) {
            return PONTO_E_VIRGULA;
        }
        for (SeparadorCsv s : values()) {
            if (s.name().equalsIgnoreCase(valor.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException(
                "Separador nao suportado: " + valor + ". Use PONTO_E_VIRGULA ou VIRGULA.");
    }
}
