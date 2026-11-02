package br.com.fatec.georural.service.pipeline.perfil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// guarda os perfis por tipo de conjunto e por nome de camada
@Slf4j
@Component
public class RegistroPerfis {

    private final Map<String, PerfilCamada> porTipo = new LinkedHashMap<>();
    private final Map<String, PerfilCamada> porCamada = new LinkedHashMap<>();

    public RegistroPerfis(List<PerfilCamada> implementacoes) {
        for (PerfilCamada perfil : implementacoes) {
            PerfilCamada anterior = porTipo.put(perfil.tipoConjunto(), perfil);
            if (anterior != null) {
                throw new IllegalStateException(
                        "Dois perfis para o tipo de conjunto " + perfil.tipoConjunto() + ".");
            }
            for (String nome : perfil.nomesDeCamada()) {
                String chave = normalizar(nome);
                PerfilCamada conflito = porCamada.put(chave, perfil);
                if (conflito != null && conflito != perfil) {
                    throw new IllegalStateException("O nome de camada '" + nome
                            + "' esta em dois perfis: " + conflito.tipoConjunto()
                            + " e " + perfil.tipoConjunto() + ".");
                }
            }
        }
        log.info("Perfis de camada registrados: {} | nomes de camada reconhecidos: {}",
                porTipo.keySet(), porCamada.keySet());
    }

    public Optional<PerfilCamada> buscar(String tipoConjunto) {
        return tipoConjunto == null ? Optional.empty() : Optional.ofNullable(porTipo.get(tipoConjunto));
    }

    public PerfilCamada para(String tipoConjunto) {
        return buscar(tipoConjunto).orElseThrow(() -> new PerfilNaoDefinidoException(tipoConjunto));
    }

    // perfil pelo nome da camada
    public Optional<PerfilCamada> porNomeDeCamada(String nomeDaCamada) {
        String chave = normalizar(nomeDaCamada);
        if (chave.isEmpty()) {
            return Optional.empty();
        }

        PerfilCamada exato = porCamada.get(chave);
        if (exato != null) {
            return Optional.of(exato);
        }

        // prefixo: AREA_IMOVEL_1, MUNICIPIOS_2023, RESERVA_LEGAL_PROPOSTA...
        return porCamada.entrySet().stream()
                .filter(e -> chave.startsWith(e.getKey()))
                .max((a, b) -> Integer.compare(a.getKey().length(), b.getKey().length()))
                .map(Map.Entry::getValue);
    }

    public List<String> tiposComPerfil() {
        return List.copyOf(porTipo.keySet());
    }

    public List<String> camadasReconhecidas() {
        return List.copyOf(porCamada.keySet());
    }

    // tira caminho, extensao e pontuacao: "SICAR/AREA_IMOVEL.shp" -> "AREA_IMOVEL"
    private String normalizar(String nome) {
        if (nome == null) {
            return "";
        }
        String texto = nome.trim();

        int marca = texto.lastIndexOf("!/");
        if (marca >= 0) {
            texto = texto.substring(marca + 2);
        }
        int barra = Math.max(texto.lastIndexOf('/'), texto.lastIndexOf('\\'));
        if (barra >= 0) {
            texto = texto.substring(barra + 1);
        }
        int ponto = texto.lastIndexOf('.');
        if (ponto > 0) {
            texto = texto.substring(0, ponto);
        }

        return texto.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    public static class PerfilNaoDefinidoException extends RuntimeException {
        public PerfilNaoDefinidoException(String tipoConjunto) {
            super("Ainda nao existe perfil de camada para o tipo de conjunto '" + tipoConjunto
                    + "'. Os tipos atendidos sao IMOVEL, RESERVA_LEGAL, EMBARGO e MALHA_MUNICIPAL.");
        }
    }
}
