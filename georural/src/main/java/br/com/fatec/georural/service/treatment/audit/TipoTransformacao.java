package br.com.fatec.georural.service.treatment.audit;

/**
 * Nomes LÓGICOS das transformações usados pelo pipeline, mais o mapeamento para
 * o que a check constraint CK_TRA_TIPO aceita em TRANSFORMACAO.TRA_TIPO:
 *
 *   VALIDACAO, PADRONIZACAO, REPROJECAO, DISSOLVER, CRUZAMENTO,
 *   CALCULO, AGREGACAO, OUTRO
 *
 * O nome lógico (ex.: RECORTE) vai no início da descrição, entre colchetes,
 * para não perder a distinção quando o tipo gravado for genérico (OUTRO).
 */
public final class TipoTransformacao {

    private TipoTransformacao() {
    }

    public static final String CARGA = "CARGA";
    public static final String VALIDACAO_CAMPOS = "VALIDACAO_CAMPOS";
    public static final String VALIDACAO_DUPLICIDADE = "VALIDACAO_DUPLICIDADE";
    public static final String PADRONIZACAO = "PADRONIZACAO";
    public static final String DISSOLVER = "DISSOLVER";
    public static final String GRAVACAO_ZONA_TRATADA = "GRAVACAO_ZONA_TRATADA";

    // para os estados que não vieram no zip (use no registrarTransformacao deles)
    public static final String REPROJECAO = "REPROJECAO";
    public static final String RECORTE = "RECORTE";
    public static final String CALCULO_AREA = "CALCULO_AREA";

    /** Converte o nome lógico no valor aceito pela CK_TRA_TIPO. */
    public static String paraBanco(String logico) {

        if (logico == null) {
            return "OUTRO";
        }

        return switch (logico) {
            case VALIDACAO_CAMPOS, VALIDACAO_DUPLICIDADE -> "VALIDACAO";
            case PADRONIZACAO -> "PADRONIZACAO";
            case DISSOLVER -> "DISSOLVER";
            case REPROJECAO -> "REPROJECAO";
            case CALCULO_AREA -> "CALCULO";
            default -> "OUTRO"; // CARGA, RECORTE, GRAVACAO_ZONA_TRATADA
        };
    }
}
