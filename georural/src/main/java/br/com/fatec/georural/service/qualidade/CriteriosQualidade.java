package br.com.fatec.georural.service.qualidade;

import java.util.Set;

public record CriteriosQualidade(
        double limiarQuarentenaPercentual,
        Set<String> severidadesBloqueantes
) {

    public CriteriosQualidade {
        if (limiarQuarentenaPercentual < 0 || limiarQuarentenaPercentual > 100) {
            throw new IllegalArgumentException("limiarQuarentenaPercentual deve estar entre 0 e 100");
        }
        severidadesBloqueantes = Set.copyOf(severidadesBloqueantes);
    }

    /** Criterios padrao do projeto */
    public static CriteriosQualidade padrao() {
        return new CriteriosQualidade(2.0, Set.of("ERRO"));
    }
}