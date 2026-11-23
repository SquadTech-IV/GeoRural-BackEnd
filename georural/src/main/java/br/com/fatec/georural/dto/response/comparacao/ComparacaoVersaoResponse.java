package br.com.fatec.georural.dto.response.comparacao;

import br.com.fatec.georural.dto.response.PaginaResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * US#19 - Comparacao entre duas versoes publicadas de um indicador.
 * A (referencia) x B (comparada): diferenca = B - A.
 */
public record ComparacaoVersaoResponse(
        Indicador indicador,
        VersaoPublicada versaoA,
        VersaoPublicada versaoB,
        Resumo resumo,
        List<MotivoMudanca> motivos,
        List<String> lacunas,
        PaginaResponse<ImovelComparado> imoveis
) {

    public record Indicador(String sigla, String nome, String unidade) {}

    /** US#19.01 - O que identifica uma versao publicada de indicador. */
    public record VersaoPublicada(
            Long versaoId,
            Long numero,
            String hash,
            String competencia,
            String situacao,
            LocalDateTime dataPublicacao,
            Long conjuntoId,
            String conjuntoNome,
            Long fonteId,
            String fonteNome,
            Long execucaoId,
            String dagRunId,
            Regra regra,
            List<EntradaOrigem> entradas
    ) {}

    public record Regra(Long id, String versao, String formula, List<Parametro> parametros) {}

    public record Parametro(String nome, String rotulo, String valor, String unidade) {}

    /** Versao de conjunto de dados que alimentou o calculo (dado de origem). */
    public record EntradaOrigem(
            Long versaoId,
            Long numero,
            String competencia,
            String hash,
            Long conjuntoId,
            String conjuntoNome,
            String fonteNome
    ) {}

    public enum TipoMotivo { REGRA, PARAMETRO, DADO_DE_ORIGEM, NAO_IDENTIFICADO }

    /** US#19.03 - Motivo da mudanca, com o detalhe do que mudou. */
    public record MotivoMudanca(TipoMotivo tipo, String descricao, List<String> detalhes) {}

    public record Resumo(long totalImoveis, long iguais, long alterados, long apenasEmA, long apenasEmB) {}

    public enum SituacaoImovel { IGUAL, ALTERADO, APENAS_EM_A, APENAS_EM_B }

    public record Valores(BigDecimal percentual, BigDecimal hectares, BigDecimal absoluto, String situacao) {}

    public record Diferenca(BigDecimal percentual, BigDecimal hectares, BigDecimal absoluto) {}

    /** US#19.02 - Linha da comparacao por imovel. */
    public record ImovelComparado(
            Long imovelId,
            String codigoCar,
            String nome,
            SituacaoImovel situacao,
            Valores valorA,
            Valores valorB,
            Diferenca diferenca,
            List<TipoMotivo> motivos
    ) {}
}
