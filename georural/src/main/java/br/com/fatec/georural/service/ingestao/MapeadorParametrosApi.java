package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.FormatoRespostaApi;
import br.com.fatec.georural.dto.ingestao.ParametrosConsultaApi;
import br.com.fatec.georural.dto.ingestao.TipoAutenticacaoApi;
import br.com.fatec.georural.entity.ConjuntoDados;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MapeadorParametrosApi {

    public ParametrosConsultaApi montar(ConjuntoDados conjunto, Map<String, String> filtros) {
        return new ParametrosConsultaApi(
                conjunto.getApiEndpoint(),
                FormatoRespostaApi.doCadastro(conjunto.getApiFormato()),
                TipoAutenticacaoApi.doCadastro(conjunto.getApiAuth()),
                extrairCredencial(conjunto.getApiAuth()),
                filtros == null ? Map.of() : filtros
        );
    }

    private static String extrairCredencial(String conApiAuth) {
        if (conApiAuth == null || conApiAuth.isBlank()) return null;
        String v = conApiAuth.trim();
        int i = v.indexOf(':');
        return (i < 0) ? null : v.substring(i + 1);
    }
}