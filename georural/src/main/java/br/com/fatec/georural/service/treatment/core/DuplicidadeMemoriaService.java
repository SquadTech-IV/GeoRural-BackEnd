package br.com.fatec.georural.service.treatment.core;

import lombok.extern.slf4j.Slf4j;
import org.geotools.api.feature.simple.SimpleFeature;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * Serviço responsável por validar duplicidades de registros em arquivos grandes
 * utilizando hashing de 64-bits em memória (US#08 - T06 / Regra VAL-004).
 */
@Slf4j
@Service
public class DuplicidadeMemoriaService {

    /**
     * Estrutura leve para rastreamento de duplicidades por execução.
     */
    public static class RastreadorDuplicidade {
        private final Set<Long> hashesVistos = new HashSet<>();

        public boolean adicionarEVerificarDuplicado(String chaveUnica) {
            if (chaveUnica == null || chaveUnica.isBlank()) {
                return false;
            }
            long hash64 = calcularHash64(chaveUnica);
            return !hashesVistos.add(hash64);
        }

        public void limpar() {
            hashesVistos.clear();
        }

        public int totalRegistrados() {
            return hashesVistos.size();
        }
    }

    public RastreadorDuplicidade criarRastreador() {
        return new RastreadorDuplicidade();
    }

    /**
     * Extrai a chave de identidade do registro com base no tipo de camada/conjunto.
     */
    public String extrairChaveIdentificadora(SimpleFeature feature) {
        if (feature == null) {
            return null;
        }

        // 1. Camada de Embargo: seq_tad ou num_tad
        Object seqTad = feature.getAttribute("seq_tad");
        if (seqTad != null && !seqTad.toString().isBlank()) {
            return "EMBARGO:" + seqTad.toString().trim();
        }
        Object numTad = feature.getAttribute("num_tad");
        if (numTad != null && !numTad.toString().isBlank()) {
            return "EMBARGO:" + numTad.toString().trim();
        }

        // 2. Camada de Imóvel / Área de Propriedade: recibo + tema
        Object recibo = feature.getAttribute("recibo");
        Object tema = feature.getAttribute("tema");
        if (recibo != null && !recibo.toString().isBlank()) {
            String t = tema != null ? tema.toString().trim() : "";
            return "IMOVEL:" + recibo.toString().trim() + "|" + t;
        }

        // 3. Fallback: ID nativo da feição ou hash do conteúdo do registro
        if (feature.getID() != null && !feature.getID().isBlank()) {
            return "FEATURE_ID:" + feature.getID();
        }

        return null;
    }

    public static long calcularHash64(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            long hash = 0;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xff);
            }
            return hash;
        } catch (NoSuchAlgorithmException e) {
            return texto.hashCode();
        }
    }
}