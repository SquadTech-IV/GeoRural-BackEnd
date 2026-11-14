package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.pipeline.RodadaDetalheResponse;
import br.com.fatec.georural.service.pipeline.RodadaDetalheService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/pipeline/execucoes")
@Tag(
        name = "Detalhe da rodada",
        description = "As etapas de uma rodada com as tentativas de cada uma (US#10.02)"
)
public class RodadaDetalheController {

    private final RodadaDetalheService servico;

    public RodadaDetalheController(RodadaDetalheService servico) {
        this.servico = servico;
    }

    @GetMapping("/{dagRunId}")
    @Operation(
            summary = "Detalha uma rodada etapa por etapa",
            description = "Devolve as seis etapas do fluxo, inclusive as que nao rodaram, "
                    + "cada uma com a situacao da ultima tentativa, o historico numerado "
                    + "das tentativas e se da para reexecutar. A situacao da rodada olha "
                    + "so a ultima tentativa de cada etapa, para que uma falha ja "
                    + "reexecutada com sucesso nao deixe a rodada como FALHOU."
    )
    public RodadaDetalheResponse detalhar(
            @Parameter(description = "Identificador da rodada, o mesmo dag_run_id da trilha")
            @PathVariable String dagRunId
    ) {
        return servico.detalhar(dagRunId);
    }
}
