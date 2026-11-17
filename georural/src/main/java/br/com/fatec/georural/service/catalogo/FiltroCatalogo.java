package br.com.fatec.georural.service.catalogo;

import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import java.util.List;
import java.util.Locale;

// Filtros e paginacao aceitos pela listagem do catalogo.
public record FiltroCatalogo(
        Long fonteId,
        String fonteSigla,
        String tipo,
        String situacao,
        String formaEntrada,
        String cobertura,
        String competencia,
        String indicador,
        String texto,
        boolean comVersaoVigente,
        boolean incluirInativos,
        int pagina,
        int tamanho,
        String ordenar
) {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 200;

    public static final List<String> ORDENACOES = List.of(
            "nome",
            "atualizacao",
            "acervo",
            "situacao",
            "cadastro"
    );

    private static final List<String> SITUACOES = List.of(
            "RASCUNHO",
            "EM_VALIDACAO",
            "PUBLICADO",
            "ARQUIVADO"
    );

    private static final List<String> FORMAS_ENTRADA = List.of(
            "UPLOAD",
            "API",
            "AMBOS"
    );

    // Normaliza e valida o que veio na query string.
    public static FiltroCatalogo montar(
            Long fonteId,
            String fonteSigla,
            String tipo,
            String situacao,
            String formaEntrada,
            String cobertura,
            String competencia,
            String indicador,
            String texto,
            Boolean comVersaoVigente,
            Boolean incluirInativos,
            Integer pagina,
            Integer tamanho,
            String ordenar) {
        int paginaUsada = pagina == null ? 0 : pagina;

        if (paginaUsada < 0) {
            throw new RequisicaoInvalidaException(
                    "A pagina nao pode ser negativa."
            );
        }

        int tamanhoUsado = tamanho == null
                ? TAMANHO_PADRAO
                : tamanho;

        if (tamanhoUsado < 1 || tamanhoUsado > TAMANHO_MAXIMO) {
            throw new RequisicaoInvalidaException(
                    "O tamanho da pagina deve estar entre 1 e "
                            + TAMANHO_MAXIMO + "."
            );
        }

        String ordemUsada = vazio(ordenar)
                ? "nome"
                : ordenar.trim().toLowerCase(Locale.ROOT);

        if (!ORDENACOES.contains(ordemUsada)) {
            throw new RequisicaoInvalidaException(
                    "Ordenacao invalida: " + ordenar
                            + ". Use uma destas: "
                            + String.join(", ", ORDENACOES) + "."
            );
        }

        return new FiltroCatalogo(
                fonteId,
                maiusculo(fonteSigla),
                maiusculo(tipo),
                validar(maiusculo(situacao), SITUACOES, "situacao"),
                validar(maiusculo(formaEntrada), FORMAS_ENTRADA, "formaEntrada"),
                maiusculo(cobertura),
                limpo(competencia),
                maiusculo(indicador),
                maiusculo(texto),
                Boolean.TRUE.equals(comVersaoVigente),
                Boolean.TRUE.equals(incluirInativos),
                paginaUsada,
                tamanhoUsado,
                ordemUsada
        );
    }

    private static String validar(
            String valor,
            List<String> aceitos,
            String campo) {
        if (valor != null && !aceitos.contains(valor)) {
            throw new RequisicaoInvalidaException(
                    "Valor invalido para " + campo + ": " + valor
                            + ". Use um destes: "
                            + String.join(", ", aceitos) + "."
            );
        }

        return valor;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String limpo(String valor) {
        return vazio(valor) ? null : valor.trim();
    }

    private static String maiusculo(String valor) {
        String limpo = limpo(valor);

        return limpo == null
                ? null
                : limpo.toUpperCase(Locale.ROOT);
    }
}
