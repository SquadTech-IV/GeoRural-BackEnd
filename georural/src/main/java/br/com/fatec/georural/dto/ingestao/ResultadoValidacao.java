package br.com.fatec.georural.dto.ingestao;

import java.util.List;

public record ResultadoValidacao(boolean valido, List<String> problemas) {

    public ResultadoValidacao {
        problemas = (problemas == null) ? List.of() : List.copyOf(problemas);
    }

    public static ResultadoValidacao ok() {
        return new ResultadoValidacao(true, List.of());
    }

    public static ResultadoValidacao invalido(List<String> problemas) {
        return new ResultadoValidacao(false, problemas);
    }

    /** Junta os problemas em uma frase unica. */
    public String resumo() {
        return problemas.isEmpty() ? "ok" : String.join(" ", problemas);
    }
}