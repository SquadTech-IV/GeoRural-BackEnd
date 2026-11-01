package br.com.fatec.georural.service.pipeline.perfil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// guarda os perfis por tipo de conjunto
@Slf4j
@Component
public class RegistroPerfis {

    private final Map<String, PerfilCamada> perfis = new LinkedHashMap<>();

    public RegistroPerfis(List<PerfilCamada> implementacoes) {
        for (PerfilCamada perfil : implementacoes) {
            PerfilCamada anterior = perfis.put(perfil.tipoConjunto(), perfil);
            if (anterior != null) {
                throw new IllegalStateException(
                        "Dois perfis para o tipo de conjunto " + perfil.tipoConjunto() + ".");
            }
        }
        log.info("Perfis de camada registrados: {}", perfis.keySet());
    }

    public Optional<PerfilCamada> buscar(String tipoConjunto) {
        return tipoConjunto == null ? Optional.empty() : Optional.ofNullable(perfis.get(tipoConjunto));
    }

    public PerfilCamada para(String tipoConjunto) {
        return buscar(tipoConjunto)
                .orElseThrow(() -> new PerfilNaoDefinidoException(tipoConjunto));
    }

    public List<String> tiposComPerfil() {
        return List.copyOf(perfis.keySet());
    }

    public static class PerfilNaoDefinidoException extends RuntimeException {
        public PerfilNaoDefinidoException(String tipoConjunto) {
            super("Ainda nao existe perfil de camada para o tipo de conjunto '" + tipoConjunto
                    + "'. Os tipos atendidos sao IMOVEL, RESERVA_LEGAL, EMBARGO e MALHA_MUNICIPAL.");
        }
    }
}
