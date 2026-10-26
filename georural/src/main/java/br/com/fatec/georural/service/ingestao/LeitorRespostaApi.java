package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ConteudoRecebido;
import br.com.fatec.georural.dto.ingestao.FormatoRespostaApi;
import br.com.fatec.georural.dto.ingestao.RespostaApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LeitorRespostaApi {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern GML_MEMBER =
            Pattern.compile("<(?:[\\w-]+:)?(?:featureMember|member)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern KML_PLACEMARK =
            Pattern.compile("<(?:[\\w-]+:)?Placemark\\b", Pattern.CASE_INSENSITIVE);

    public ConteudoRecebido ler(RespostaApi resposta, FormatoRespostaApi formatoEsperado) {
        byte[] bytes = resposta.conteudo() == null ? new byte[0] : resposta.conteudo();
        FormatoRespostaApi formato = (formatoEsperado != null) ? formatoEsperado : FormatoRespostaApi.JSON;
        String texto = resposta.corpoTexto();

        long qtd;
        boolean bemFormado;
        try {
            switch (formato) {
                case GEOJSON, JSON -> {
                    long c = contarJson(texto);
                    qtd = Math.max(c, 0);
                    bemFormado = (c >= 0);
                }
                case CSV -> {
                    qtd = contarCsv(texto);
                    bemFormado = !texto.isBlank();
                }
                case WFS_GML -> {
                    qtd = contarRegex(texto, GML_MEMBER);
                    bemFormado = texto.contains("<");
                }
                case KML -> {
                    qtd = contarRegex(texto, KML_PLACEMARK);
                    bemFormado = texto.contains("<");
                }
                default -> {
                    qtd = 0;
                    bemFormado = false;
                }
            }
        } catch (Exception e) {
            qtd = 0;
            bemFormado = false;
        }

        return new ConteudoRecebido(formato, qtd, bytes.length, bemFormado, bytes);
    }

    private long contarJson(String texto) {
        if (texto == null || texto.isBlank()) return 0;
        try {
            JsonNode raiz = objectMapper.readTree(texto);
            if (raiz == null || raiz.isNull()) return 0;
            JsonNode features = raiz.get("features");
            if (features != null && features.isArray()) return features.size();
            if (raiz.isArray()) return raiz.size();
            return 1;
        } catch (Exception e) {
            return -1;
        }
    }

    private long contarCsv(String texto) {
        if (texto == null || texto.isBlank()) return 0;
        long linhas = texto.lines().filter(l -> !l.isBlank()).count();
        return Math.max(linhas - 1, 0);
    }

    private long contarRegex(String texto, Pattern padrao) {
        if (texto == null || texto.isBlank()) return 0;
        Matcher m = padrao.matcher(texto);
        long c = 0;
        while (m.find()) {
            c++;
        }
        return c;
    }
}