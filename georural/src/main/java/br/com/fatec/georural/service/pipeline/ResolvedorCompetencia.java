package br.com.fatec.georural.service.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@Component
public class ResolvedorCompetencia {

    private static final DateTimeFormatter ANO_MES = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 2024-10, 2024_10 ou 202410 */
    private static final Pattern ANO_MES_NO_NOME =
            Pattern.compile("(19|20)\\d{2}[-_]?(0[1-9]|1[0-2])(?!\\d)");
    /** 2024 isolado */
    private static final Pattern ANO_NO_NOME =
            Pattern.compile("(?<!\\d)((19|20)\\d{2})(?!\\d)");

    private static final String[] CHAVES_API =
            {"competencia", "ano", "year", "anoReferencia", "ano_referencia", "data", "dataReferencia"};

    public String resolver(String informado, Map<String, String> parametrosApi, String nomeArquivo) {
        String porInformado = normalizar(informado);
        if (porInformado != null) {
            return porInformado;
        }

        String porApi = dosParametros(parametrosApi);
        if (porApi != null) {
            log.debug("Competencia '{}' veio dos parametros da API.", porApi);
            return porApi;
        }

        String porNome = doNomeArquivo(nomeArquivo);
        if (porNome != null) {
            log.debug("Competencia '{}' deduzida do nome do arquivo '{}'.", porNome, nomeArquivo);
            return porNome;
        }

        String corrente = LocalDate.now().format(ANO_MES);
        log.info("Competencia nao informada para '{}'; assumindo o mes corrente ({}). "
                + "Informe a competencia na ingestao para separar corretamente os periodos.",
                nomeArquivo, corrente);
        return corrente;
    }

    public String resolver(String informado, String nomeArquivo) {
        return resolver(informado, null, nomeArquivo);
    }

    private String dosParametros(Map<String, String> parametros) {
        if (parametros == null || parametros.isEmpty()) {
            return null;
        }
        for (String chave : CHAVES_API) {
            for (Map.Entry<String, String> e : parametros.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(chave)) {
                    String v = normalizar(e.getValue());
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private String doNomeArquivo(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        Matcher anoMes = ANO_MES_NO_NOME.matcher(nome);
        if (anoMes.find()) {
            String achado = anoMes.group().replaceAll("[-_]", "");
            return achado.substring(0, 4) + "-" + achado.substring(4, 6);
        }
        Matcher ano = ANO_NO_NOME.matcher(nome);
        if (ano.find()) {
            return ano.group(1);
        }
        return null;
    }

    /** Aceita "2024", "2024-10", "2024/10", "10/2024" e devolve sempre yyyy ou yyyy-MM. */
    private String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String v = valor.trim();

        if (v.matches("(19|20)\\d{2}")) {
            return v;
        }
        if (v.matches("(19|20)\\d{2}[-/_](0[1-9]|1[0-2])")) {
            return v.substring(0, 4) + "-" + v.substring(5, 7);
        }
        if (v.matches("(0[1-9]|1[0-2])[-/_](19|20)\\d{2}")) {
            return v.substring(3, 7) + "-" + v.substring(0, 2);
        }
        if (v.matches("(19|20)\\d{2}-(0[1-9]|1[0-2])-\\d{2}.*")) {
            return v.substring(0, 7);
        }
        if (v.length() > 20) {
            return v.substring(0, 20);
        }
        return v;
    }
}
