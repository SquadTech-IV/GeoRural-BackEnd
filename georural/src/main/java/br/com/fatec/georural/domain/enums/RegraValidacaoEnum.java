package br.com.fatec.georural.domain.enums;

/**
 * Catálogo das 7 Regras de Validação para a Zona de Quarentena.
 */
public enum RegraValidacaoEnum {

    VAL_001("VAL-001", "Campos Obrigatórios", "Valida a presença de todos os campos declarados como obrigatórios no esquema do conjunto de dados.", "Campo obrigatório ausente ou nulo: {campo}"),
    VAL_002("VAL-002", "Tipos de Dados", "Garante que o tipo de dado de cada campo seja compatível com o esquema esperado.", "Valor do campo '{campo}' incompatível com o tipo '{tipoEsperado}'"),
    VAL_003("VAL-003", "Domínio de Valores", "Verifica se o valor informado pertence ao conjunto de domínios/valores aceitos.", "Valor '{valor}' fora do domínio permitido para o campo '{campo}'"),
    VAL_004("VAL-004", "Duplicidade", "Detecta registros duplicados no arquivo processado sem estourar o limite de memória.", "Registro duplicado detectado com a chave/hash: {chave}"),
    VAL_005("VAL-005", "Áreas", "Valida se os valores de área declarados são positivos e coerentes.", "Valor de área inválido ou fora dos limites permitidos: {area}"),
    VAL_006("VAL-006", "Coordenadas", "Verifica se as coordenadas estão dentro dos limites geográficos e de projeção válidos.", "Coordenadas geográficas inválidas ou fora dos limites aceitos"),
    VAL_007("VAL-007", "Geometrias", "Valida a integridade topológica da geometria e a possibilidade de reparo autônomo.", "Geometria inválida e não passível de reparo autônomo");

    private final String codigo;
    private final String nome;
    private final String descricao;
    private final String mensagemPadrao;

    RegraValidacaoEnum(String codigo, String nome, String descricao, String mensagemPadrao) {
        this.codigo = codigo;
        this.nome = nome;
        this.descricao = descricao;
        this.mensagemPadrao = mensagemPadrao;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getMensagemPadrao() {
        return mensagemPadrao;
    }
}