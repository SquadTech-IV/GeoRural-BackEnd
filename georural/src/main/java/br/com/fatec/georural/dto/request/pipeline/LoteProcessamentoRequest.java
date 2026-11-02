package br.com.fatec.georural.dto.request.pipeline;

import java.util.List;
import java.util.Map;

// disparo de varios arquivos de uma vez
public record LoteProcessamentoRequest(
        Long conjuntoId,
        Long usuarioId,
        String competencia,
        // arquivos escolhidos; vazio significa "todos os pendentes do conjunto"
        List<Long> arquivoBrutoIds,
        // continua depois de um arquivo falhar (padrao: continua)
        Boolean pararNoPrimeiroErro,
        Map<String, String> parametros) {
}
