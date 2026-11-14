package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Filtros e paginacao aceitos pelas consultas de resultados e metricas.
public record FiltroIndicador(
        List<String> indicadores,
        Long versaoId,
        Long imovelId,
        String codigoCar,
        Long municipioId,
        String municipio,
        String competencia,
        String situacao,
        Boolean vigente,
        BigDecimal valorMinimo,
        BigDecimal valorMaximo,
        boolean incluirDetalhes,
        int pagina,
        int tamanho,
        String ordenar
) {

    public static final int TAMANHO_PADRAO = 50;
    public static final int TAMANHO_MAXIMO = 500;

    public static final List<String> ORDENACOES = List.of(
            "imovel",
            "valor",
            "valorDesc",
            "calculo",
            "municipio"
    );

    public static final List<String> ORDENACOES_MUNICIPIO = List.of(
            "municipio",
            "valor",
            "valorDesc",
            "calculo"
    );

    public static FiltroIndicador montar(
            String indicador,
            Long versaoId,
            Long imovelId,
            String codigoCar,
            Long municipioId,
            String municipio,
            String competencia,
            String situacao,
            Boolean vigente,
            BigDecimal valorMinimo,
            BigDecimal valorMaximo,
            Boolean incluirDetalhes,
            Integer pagina,
            Integer tamanho,
            String ordenar,
            List<String> ordenacoesAceitas) {
        int paginaUsada = pagina == null ? 0 : pagina;

        if (paginaUsada < 0) {
            throw new RequisicaoInvalidaException(
                    "A pagina nao pode ser negativa."
            );
        }

        int tamanhoUsado = tamanho == null ? TAMANHO_PADRAO : tamanho;

        if (tamanhoUsado < 1 || tamanhoUsado > TAMANHO_MAXIMO) {
            throw new RequisicaoInvalidaException(
                    "O tamanho da pagina deve estar entre 1 e "
                            + TAMANHO_MAXIMO + "."
            );
        }

        String ordemUsada = vazio(ordenar)
                ? ordenacoesAceitas.get(0)
                : ordenar.trim();

        if (!ordenacoesAceitas.contains(ordemUsada)) {
            throw new RequisicaoInvalidaException(
                    "Ordenacao invalida: " + ordenar
                            + ". Use uma destas: "
                            + String.join(", ", ordenacoesAceitas) + "."
            );
        }

        if (valorMinimo != null
                && valorMaximo != null
                && valorMinimo.compareTo(valorMaximo) > 0) {
            throw new RequisicaoInvalidaException(
                    "valorMinimo nao pode ser maior que valorMaximo."
            );
        }

        return new FiltroIndicador(
                siglas(indicador),
                versaoId,
                imovelId,
                maiusculo(codigoCar),
                municipioId,
                maiusculo(municipio),
                limpo(competencia),
                maiusculo(situacao),
                vigente,
                valorMinimo,
                valorMaximo,
                Boolean.TRUE.equals(incluirDetalhes),
                paginaUsada,
                tamanhoUsado,
                ordemUsada
        );
    }

    // Aceita uma sigla ou varias separadas por virgula.
    private static List<String> siglas(String indicador) {
        if (vazio(indicador)) {
            return List.of();
        }

        List<String> siglas = new ArrayList<>();

        for (String parte : indicador.split(",")) {
            String sigla = parte.trim().toUpperCase(Locale.ROOT);

            if (!sigla.isEmpty() && !siglas.contains(sigla)) {
                siglas.add(sigla);
            }
        }

        if (siglas.size() > 20) {
            throw new RequisicaoInvalidaException(
                    "No maximo 20 indicadores por consulta."
            );
        }

        return siglas;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String limpo(String valor) {
        return vazio(valor) ? null : valor.trim();
    }

    private static String maiusculo(String valor) {
        String limpo = limpo(valor);

        return limpo == null ? null : limpo.toUpperCase(Locale.ROOT);
    }
}
