package br.com.fatec.georural.service.pipeline.validacao;

// nomes das regras de REGRA_QUALIDADE, iguais aos do V2
public final class RegrasQualidade {

    public static final String CAMPO_OBRIGATORIO_AUSENTE = "Campo obrigatorio ausente";
    public static final String CAMPO_OPCIONAL_AUSENTE = "Campo opcional ausente";
    public static final String TIPO_INVALIDO = "Tipo de dado invalido";
    public static final String FORA_DO_DOMINIO = "Valor fora do dominio";
    public static final String DUPLICADO_NO_ARQUIVO = "Registro duplicado no arquivo";
    public static final String JA_EXISTENTE_NA_BASE = "Registro ja existente na base";
    public static final String AREA_INCOMPATIVEL = "Area incompativel";
    public static final String AREA_NULA = "Area nula ou negativa";
    public static final String FORA_DA_AREA_DE_INTERESSE = "Coordenada fora da area de interesse";
    public static final String SRC_AUSENTE_OU_DIVERGENTE = "Sistema de referencia ausente ou divergente";
    public static final String GEOMETRIA_INVALIDA = "Geometria invalida";
    public static final String GEOMETRIA_AUSENTE = "Geometria ausente";
    public static final String GEOMETRIA_CORRIGIDA = "Geometria corrigida automaticamente";
    public static final String FORMATO_DIVERGENTE = "Formato divergente do declarado";
    public static final String FORMATO_NAO_SUPORTADO = "Formato nao suportado";

    private RegrasQualidade() {
    }
}
