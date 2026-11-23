package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.dto.response.indicador.MunicipioConsolidadoResponse;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import java.math.BigDecimal;
import java.util.Comparator;

/** Como a lista de municipios sai ordenada. */
public enum OrdemComparacao {

    /** Por valor, o pior do indicador primeiro. Depende do sentido. */
    PIOR_PRIMEIRO,

    /** Por valor, o melhor do indicador primeiro. Depende do sentido. */
    MELHOR_PRIMEIRO,

    /** Por valor cru, do menor para o maior. Ignora o sentido. */
    VALOR_CRESCENTE,

    /** Por valor cru, do maior para o menor. Ignora o sentido. */
    VALOR_DECRESCENTE,

    /** Alfabetica. */
    NOME,

    /** Quem tem mais imovel no agregado primeiro: onde o numero pesa mais. */
    MAIS_IMOVEIS,

    /** Menor percentual de conformes primeiro: onde esta o problema. */
    MENOS_CONFORMES;

    public static OrdemComparacao de(String valor) {
        if (valor == null || valor.isBlank()) {
            return PIOR_PRIMEIRO;
        }

        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException(
                    "ordenacao invalida: " + valor + ". Use uma de "
                            + java.util.Arrays.toString(values()) + ".");
        }
    }

    /** Municipio sem dado vai para o fim em qualquer ordenacao. */
    public Comparator<MunicipioConsolidadoResponse> comparador(SentidoIndicador sentido) {
        Comparator<MunicipioConsolidadoResponse> semDadoNoFim =
                Comparator.comparing(m -> !m.temDados());

        return semDadoNoFim
                .thenComparing(criterio(sentido))
                .thenComparing(m -> m.nome() == null ? "" : m.nome(),
                        String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<MunicipioConsolidadoResponse> criterio(SentidoIndicador sentido) {
        switch (this) {
            case NOME:
                return Comparator.comparing(
                        m -> m.nome() == null ? "" : m.nome(), String.CASE_INSENSITIVE_ORDER);

            case VALOR_CRESCENTE:
                return porValor(true);

            case VALOR_DECRESCENTE:
                return porValor(false);

            case PIOR_PRIMEIRO:
                // pior = menor valor quando maior e melhor, e o contrario quando nao
                return porValor(sentido.maiorEMelhor());

            case MELHOR_PRIMEIRO:
                return porValor(!sentido.maiorEMelhor());

            case MAIS_IMOVEIS:
                return Comparator.comparing(
                        MunicipioConsolidadoResponse::imoveis,
                        Comparator.nullsLast(Comparator.reverseOrder()));

            case MENOS_CONFORMES:
                return Comparator.comparing(
                        MunicipioConsolidadoResponse::percentualConformes,
                        Comparator.nullsLast(Comparator.naturalOrder()));

            default:
                return Comparator.comparing(
                        m -> m.nome() == null ? "" : m.nome(), String.CASE_INSENSITIVE_ORDER);
        }
    }

    private static Comparator<MunicipioConsolidadoResponse> porValor(boolean crescente) {
        Comparator<BigDecimal> dentro = crescente
                ? Comparator.naturalOrder()
                : Comparator.reverseOrder();

        return Comparator.comparing(
                MunicipioConsolidadoResponse::valorAgregado,
                Comparator.nullsLast(dentro));
    }
}
