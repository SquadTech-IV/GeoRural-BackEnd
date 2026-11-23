package br.com.fatec.georural.service.indicador;

/** Para que lado o indicador melhora. Vem de INDICADOR.ind_sentido. */
public enum SentidoIndicador {

    /** Valor alto e bom: IRL, ICV, IAPP. */
    MAIOR_MELHOR,

    /** Valor alto e problema: IAE, ISAP, IDESMAT, IFC. */
    MENOR_MELHOR;

    public static SentidoIndicador de(String valor) {
        if (valor == null) {
            return MAIOR_MELHOR;
        }

        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MAIOR_MELHOR;
        }
    }

    public boolean maiorEMelhor() {
        return this == MAIOR_MELHOR;
    }
}
