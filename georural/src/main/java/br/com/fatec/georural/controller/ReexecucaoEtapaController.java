package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.pipeline.ReexecucaoEtapaRequest;
import br.com.fatec.georural.dto.response.pipeline.ReexecucaoEtapaResponse;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.service.pipeline.Etapa;
import br.com.fatec.georural.service.pipeline.PipelineExecutor;
import br.com.fatec.georural.service.pipeline.ReexecucaoEtapaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/pipeline/execucoes")
@Tag(
        name = "Reexecucao de etapa",
        description = "Roda de novo uma etapa de uma rodada que ja existe, remontando "
                + "o contexto a partir da trilha (US#10.01)"
)
public class ReexecucaoEtapaController {

    private final ReexecucaoEtapaService servico;

    public ReexecucaoEtapaController(ReexecucaoEtapaService servico) {
        this.servico = servico;
    }

    @PostMapping("/{dagRunId}/etapas/{etapa}/reexecutar")
    @Operation(
            summary = "Reexecuta uma etapa de uma rodada existente",
            description = "Le a rodada e os parametros que ela gravou, remonta o contexto "
                    + "sozinho e roda a etapa de novo com o mesmo dagRunId, gerando uma "
                    + "tentativa nova na trilha. O corpo e opcional: serve para trocar o "
                    + "usuario, sobrescrever parametros ou forcar a reexecucao de uma "
                    + "etapa que deu certo."
    )
    public ReexecucaoEtapaResponse reexecutar(
            @Parameter(description = "Identificador da rodada, o mesmo dag_run_id da trilha")
            @PathVariable String dagRunId,

            @Parameter(description = "Etapa a reexecutar: INGESTAO, VALIDACAO, TRATAMENTO, "
                    + "CALCULO, QUALIDADE ou PUBLICACAO")
            @PathVariable String etapa,

            @RequestBody(required = false) ReexecucaoEtapaRequest pedido
    ) {
        return servico.reexecutar(dagRunId, converter(etapa), pedido);
    }

    private Etapa converter(String etapa) {
        try {
            return Etapa.valueOf(etapa.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RequisicaoInvalidaException(
                    "Etapa desconhecida: '" + etapa + "'. Use uma de "
                            + Arrays.toString(Etapa.values()) + "."
            );
        }
    }

    @ExceptionHandler(PipelineExecutor.EtapaNaoImplementadaException.class)
    public ResponseEntity<Map<String, String>> naoImplementada(
            PipelineExecutor.EtapaNaoImplementadaException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("erro", e.getMessage()));
    }
}
