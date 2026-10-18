package br.com.fatec.georural.service;

import br.com.fatec.georural.exception.EstadoInconsistenteException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

public class ParametrosVigentes {

    private final String sigla;
    private final String versao;
    private final Map<String, String> valores;

    public ParametrosVigentes(String sigla, String versao, Map<String, String> valores) {
        this.sigla = sigla;
        this.versao = versao;
        this.valores = valores;
    }

    public String sigla() {
        return sigla;
    }

    public String versao() {
        return versao;
    }

    public Set<String> nomes() {
        return valores.keySet();
    }

    public BigDecimal numero(String nome) {
        String bruto = obrigatorio(nome);
        try {
            return new BigDecimal(bruto);
        } catch (NumberFormatException e) {
            throw new EstadoInconsistenteException(
                    "Parametro '" + nome + "' da regra vigente de " + sigla + " nao e numero: '" + bruto + "'.");
        }
    }


    public int inteiro(String nome) {
        return numero(nome).intValueExact();
    }

    /** Data em ISO (aaaa-mm-dd). */
    public LocalDate data(String nome) {
        String bruto = obrigatorio(nome);
        try {
            return LocalDate.parse(bruto);
        } catch (DateTimeParseException e) {
            throw new EstadoInconsistenteException(
                    "Parametro '" + nome + "' da regra vigente de " + sigla + " nao e data ISO: '" + bruto + "'.");
        }
    }

    public String texto(String nome) {
        return obrigatorio(nome);
    }

    public BigDecimal numeroOuPadrao(String nome, BigDecimal padrao) {
        return valores.containsKey(nome) ? numero(nome) : padrao;
    }

    private String obrigatorio(String nome) {
        String valor = valores.get(nome);
        if (valor == null) {
            throw new EstadoInconsistenteException(
                    "Parametro '" + nome + "' nao existe na regra vigente de " + sigla + ".");
        }
        return valor;
    }
}