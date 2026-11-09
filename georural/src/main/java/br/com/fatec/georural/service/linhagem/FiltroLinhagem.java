package br.com.fatec.georural.service.linhagem;

import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import java.util.List;
import java.util.Locale;

// Filtros e paginacao da listagem de linhagem.
public record FiltroLinhagem(
        String indicador,
        Long versaoId,
        Long imovelId,
        String codigoCar,
        Long municipioId,
        String competencia,
        String situacao,
        Boolean vigente,
        boolean apenasPublicadas,
        String texto,
        int pagina,
        int tamanho,
        String ordenar
) {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 200;

    public static final List<String> ORDENACOES = List.of(
            "calculo",
            "imovel",
            "indicador",
            "versao"
    );

    public static FiltroLinhagem montar(
            String indicador,
            Long versaoId,
            Long imovelId,
            String codigoCar,
            Long municipioId,
            String competencia,
            String situacao,
            Boolean vigente,
            Boolean apenasPublicadas,
            String texto,
            Integer pagina,
            Integer tamanho,
            String ordenar) {
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
                ? "calculo"
                : ordenar.trim().toLowerCase(Locale.ROOT);

        if (!ORDENACOES.contains(ordemUsada)) {
            throw new RequisicaoInvalidaException(
                    "Ordenacao invalida: " + ordenar
                            + ". Use uma destas: "
                            + String.join(", ", ORDENACOES) + "."
            );
        }

        return new FiltroLinhagem(
                maiusculo(indicador),
                versaoId,
                imovelId,
                maiusculo(codigoCar),
                municipioId,
                limpo(competencia),
                maiusculo(situacao),
                vigente,
                Boolean.TRUE.equals(apenasPublicadas),
                maiusculo(texto),
                paginaUsada,
                tamanhoUsado,
                ordemUsada
        );
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
