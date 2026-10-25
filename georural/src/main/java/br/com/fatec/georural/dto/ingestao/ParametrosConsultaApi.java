package br.com.fatec.georural.dto.ingestao;

import java.util.Map;

public record ParametrosConsultaApi(
        String endpoint,
        FormatoRespostaApi formato,
        TipoAutenticacaoApi autenticacao,
        String credencial,
        Map<String, String> filtros
) {
    public ParametrosConsultaApi {
        filtros = (filtros == null) ? Map.of() : Map.copyOf(filtros);
    }

    /** parametros sem filtros extras. */
    public static ParametrosConsultaApi de(String endpoint, FormatoRespostaApi formato,
                                           TipoAutenticacaoApi autenticacao, String credencial) {
        return new ParametrosConsultaApi(endpoint, formato, autenticacao, credencial, Map.of());
    }
}