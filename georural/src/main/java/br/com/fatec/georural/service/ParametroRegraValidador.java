package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.regra.ParametroInput;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@Component
public class ParametroRegraValidador {

    private static final Set<String> TIPOS_VALIDOS = Set.of("PERCENTUAL", "NUMERO", "DATA", "TEXTO");

    public void validar(List<ParametroInput> parametros) {
        if (parametros == null) {
            return;
        }
        for (ParametroInput parametro : parametros) {
            String nome = parametro.nome() == null ? "" : parametro.nome().trim();
            if (nome.isEmpty()) {
                throw new RequisicaoInvalidaException("Todo parametro precisa de um nome.");
            }

            String tipo = parametro.tipo() == null ? "" : parametro.tipo().trim().toUpperCase();
            if (!TIPOS_VALIDOS.contains(tipo)) {
                throw new RequisicaoInvalidaException(
                        "Tipo invalido no parametro '" + nome + "': use PERCENTUAL, NUMERO, DATA ou TEXTO.");
            }

            String valor = parametro.valor() == null ? "" : parametro.valor().trim();
            if (valor.isEmpty()) {
                throw new RequisicaoInvalidaException(
                        "O valor do parametro '" + nome + "' nao pode ser vazio.");
            }

            switch (tipo) {
                case "PERCENTUAL" -> validarPercentual(nome, valor);
                case "NUMERO" -> validarNumero(nome, valor);
                case "DATA" -> validarData(nome, valor);
                case "TEXTO" -> { /* ja validado como nao vazio acima */ }
            }
        }
    }

    private void validarPercentual(String nome, String valor) {
        BigDecimal numero = parseNumero(nome, valor);
        if (numero.compareTo(BigDecimal.ZERO) < 0 || numero.compareTo(new BigDecimal("100")) > 0) {
            throw new RequisicaoInvalidaException(
                    "O parametro '" + nome + "' deve ser um percentual entre 0 e 100 (recebido: " + valor + ").");
        }
    }

    private void validarNumero(String nome, String valor) {
        BigDecimal numero = parseNumero(nome, valor);
        if (numero.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RequisicaoInvalidaException(
                    "O parametro '" + nome + "' deve ser um numero maior que zero (recebido: " + valor + ").");
        }
    }

    private void validarData(String nome, String valor) {
        try {
            LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new RequisicaoInvalidaException(
                    "O parametro '" + nome + "' deve ser uma data valida no formato aaaa-mm-dd (recebido: " + valor + ").");
        }
    }

    private BigDecimal parseNumero(String nome, String valor) {
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException e) {
            throw new RequisicaoInvalidaException(
                    "O parametro '" + nome + "' deve ser um numero (recebido: " + valor + ").");
        }
    }
}