package br.com.fatec.georural.service.pipeline.perfil;

import java.util.List;
import java.util.Locale;

// um campo que a camada espera encontrar no arquivo
public record CampoCamada(
        String nome,
        TipoCampo tipo,
        boolean obrigatorio,
        List<String> aliases) {

    public static CampoCamada obrigatorio(String nome, TipoCampo tipo, String... aliases) {
        return new CampoCamada(nome, tipo, true, List.of(aliases));
    }

    public static CampoCamada opcional(String nome, TipoCampo tipo, String... aliases) {
        return new CampoCamada(nome, tipo, false, List.of(aliases));
    }

    // todos os nomes pelos quais o campo pode aparecer no arquivo
    public List<String> nomesAceitos() {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(nome), aliases.stream())
                .map(n -> n.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
