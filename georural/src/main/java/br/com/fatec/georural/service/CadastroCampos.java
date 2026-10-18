package br.com.fatec.georural.service;

import br.com.fatec.georural.exception.RequisicaoInvalidaException;

final class CadastroCampos {

    private CadastroCampos() {}

    static String obrigatorio(
        String valor,
        String campo,
        int tamanhoMaximo
    ) {
        String texto = opcional(valor, campo, tamanhoMaximo);

        if (texto == null) {
            throw new RequisicaoInvalidaException(
                "O campo " + campo + " é obrigatório."
            );
        }

        return texto;
    }

    static String opcional(
        String valor,
        String campo,
        int tamanhoMaximo
    ) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        String texto = valor.trim();

        if (texto.length() > tamanhoMaximo) {
            throw new RequisicaoInvalidaException(
                "O campo " + campo + " deve possuir no máximo " +
                tamanhoMaximo + " caracteres."
            );
        }

        return texto;
    }

    static void validarId(Long id, String recurso) {
        if (id == null || id <= 0) {
            throw new RequisicaoInvalidaException(
                "Informe um identificador válido para " + recurso + "."
            );
        }
    }
}