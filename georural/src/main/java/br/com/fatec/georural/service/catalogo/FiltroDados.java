package br.com.fatec.georural.service.catalogo;

import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import java.util.Locale;

// Filtros e paginacao aceitos pela consulta dos dados de um conjunto.
public record FiltroDados(
        Long versaoId,
        Long imovelId,
        String texto,
        boolean incluirGeometria,
        int pagina,
        int tamanho
) {

    public static final int TAMANHO_PADRAO = 50;
    public static final int TAMANHO_MAXIMO = 500;
    public static final int TAMANHO_MAXIMO_COM_GEOMETRIA = 50;

    public static FiltroDados montar(
            Long versaoId,
            Long imovelId,
            String texto,
            Boolean incluirGeometria,
            Integer pagina,
            Integer tamanho) {
        int paginaUsada = pagina == null ? 0 : pagina;

        if (paginaUsada < 0) {
            throw new RequisicaoInvalidaException(
                    "A pagina nao pode ser negativa."
            );
        }

        boolean geometria = Boolean.TRUE.equals(incluirGeometria);

        int teto = geometria
                ? TAMANHO_MAXIMO_COM_GEOMETRIA
                : TAMANHO_MAXIMO;

        int tamanhoUsado = tamanho == null
                ? Math.min(TAMANHO_PADRAO, teto)
                : tamanho;

        if (tamanhoUsado < 1 || tamanhoUsado > teto) {
            throw new RequisicaoInvalidaException(
                    geometria
                            ? "Com incluirGeometria=true o tamanho da pagina deve estar entre 1 e "
                                    + TAMANHO_MAXIMO_COM_GEOMETRIA + "."
                            : "O tamanho da pagina deve estar entre 1 e "
                                    + TAMANHO_MAXIMO + "."
            );
        }

        if (versaoId != null && versaoId <= 0) {
            throw new RequisicaoInvalidaException(
                    "versaoId invalido: " + versaoId + "."
            );
        }

        if (imovelId != null && imovelId <= 0) {
            throw new RequisicaoInvalidaException(
                    "imovelId invalido: " + imovelId + "."
            );
        }

        String textoUsado = texto == null || texto.isBlank()
                ? null
                : texto.trim().toUpperCase(Locale.ROOT);

        return new FiltroDados(
                versaoId,
                imovelId,
                textoUsado,
                geometria,
                paginaUsada,
                tamanhoUsado
        );
    }
}
