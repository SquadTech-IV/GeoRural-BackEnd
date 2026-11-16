package br.com.fatec.georural.dto.request.pipeline;

import java.util.Map;

// Corpo opcional da reexecucao: tudo o que nao vier e recuperado da rodada.
public record ReexecucaoEtapaRequest(
        Long usuarioId,
        Boolean forcar,
        Map<String, String> parametros) {
}
