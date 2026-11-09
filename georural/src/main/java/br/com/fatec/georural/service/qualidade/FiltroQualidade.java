package br.com.fatec.georural.service.qualidade;

import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

// Filtros e paginacao aceitos pelas consultas de qualidade.
public record FiltroQualidade(
        Long conjuntoId,
        Long execucaoId,
        Long arquivoId,
        String status,
        String competencia,
        String fonteSigla,
        String tipo,
        String severidade,
        LocalDate desde,
        LocalDate ate,
        Boolean comQuarentena,
        String texto,
        int pagina,
        int tamanho,
        String ordenar
) {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 200;

    public static final List<String> ORDENACOES = List.of(
            "data",
            "aprovacao",
            "invalidos",
            "arquivo"
    );

    private static final List<String> STATUS = List.of("APROVADO", "REJEITADO");

    private static final List<String> SEVERIDADES = List.of("ERRO", "AVISO");

    public static FiltroQualidade montar(
            Long conjuntoId,
            Long execucaoId,
            Long arquivoId,
            String status,
            String competencia,
            String fonteSigla,
            String tipo,
            String severidade,
            LocalDate desde,
            LocalDate ate,
            Boolean comQuarentena,
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
                ? "data"
                : ordenar.trim().toLowerCase(Locale.ROOT);

        if (!ORDENACOES.contains(ordemUsada)) {
            throw new RequisicaoInvalidaException(
                    "Ordenacao invalida: " + ordenar
                            + ". Use uma destas: "
                            + String.join(", ", ORDENACOES) + "."
            );
        }

        if (desde != null && ate != null && desde.isAfter(ate)) {
            throw new RequisicaoInvalidaException(
                    "A data inicial nao pode ser depois da data final."
            );
        }

        return new FiltroQualidade(
                conjuntoId,
                execucaoId,
                arquivoId,
                validar(maiusculo(status), STATUS, "status"),
                limpo(competencia),
                maiusculo(fonteSigla),
                maiusculo(tipo),
                validar(maiusculo(severidade), SEVERIDADES, "severidade"),
                desde,
                ate,
                comQuarentena,
                maiusculo(texto),
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

        return limpo == null ? null : limpo.toUpperCase(Locale.ROOT);
    }
}
