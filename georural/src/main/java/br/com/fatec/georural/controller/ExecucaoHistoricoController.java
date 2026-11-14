package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.pipeline.ExecucaoDetalheResponse;
import br.com.fatec.georural.dto.response.pipeline.ExecucaoItemResponse;
import br.com.fatec.georural.dto.response.pipeline.RodadaPipelineResponse;
import br.com.fatec.georural.dto.response.pipeline.SituacaoPipelineResponse;
import br.com.fatec.georural.service.pipeline.FiltroExecucao;
import br.com.fatec.georural.service.pipeline.HistoricoExecucaoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

// Historico e situacao das execucoes das pipelines (US#17.06).
@RestController
@RequestMapping("/api/v1/execucoes")
@Tag(
        name = "Execucoes das pipelines",
        description = "Historico das execucoes por etapa e por rodada, detalhe de uma "
                + "execucao e a situacao atual do pipeline (US#17.06)"
)
public class ExecucaoHistoricoController {

    private final HistoricoExecucaoService servico;

    public ExecucaoHistoricoController(HistoricoExecucaoService servico) {
        this.servico = servico;
    }

    @GetMapping
    @Operation(
            summary = "Lista as execucoes, uma linha por etapa",
            description = "Pagina de execucoes com etapa, status, conjunto, arquivo, "
                    + "quem disparou, contagens de registros e duracao."
    )
    public PaginaResponse<ExecucaoItemResponse> execucoes(
            @Parameter(description = "Identificador do conjunto de dados")
            @RequestParam(required = false) Long conjuntoId,

            @Parameter(description = "Etapa: CADASTRO, INGESTAO, VALIDACAO, TRATAMENTO, "
                    + "CALCULO, QUALIDADE, PUBLICACAO ou CONSUMO")
            @RequestParam(required = false) String etapa,

            @Parameter(description = "Status: EM_ANDAMENTO, CONCLUIDA, COM_RESSALVA ou FALHOU")
            @RequestParam(required = false) String status,

            @Parameter(description = "Identificador da rodada no Airflow")
            @RequestParam(required = false) String dagRunId,

            @Parameter(description = "Identificador do arquivo bruto")
            @RequestParam(required = false) Long arquivoId,

            @Parameter(description = "Competencia do arquivo, no formato AAAA-MM")
            @RequestParam(required = false) String competencia,

            @Parameter(description = "Sigla da fonte do dado")
            @RequestParam(required = false) String fonteSigla,

            @Parameter(description = "Codigo do tipo de camada")
            @RequestParam(required = false) String tipo,

            @Parameter(description = "true traz so as execucoes automaticas, false so as manuais")
            @RequestParam(required = false) Boolean automatica,

            @Parameter(description = "true traz so o que teve falha na rodada, false so o que nao teve")
            @RequestParam(required = false) Boolean comFalha,

            @Parameter(description = "Data inicial, no formato AAAA-MM-DD")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,

            @Parameter(description = "Data final, incluida por inteiro")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,

            @Parameter(description = "Busca no conjunto, no arquivo, no dagRunId e na mensagem")
            @RequestParam(required = false) String q,

            @Parameter(description = "Pagina, comecando em 0")
            @RequestParam(required = false) Integer pagina,

            @Parameter(description = "Itens por pagina, de 1 a 200. Padrao 20")
            @RequestParam(required = false) Integer tamanho,

            @Parameter(description = "Ordenacao: inicio, duracao, etapa ou conjunto")
            @RequestParam(required = false) String ordenar
    ) {
        return servico.execucoes(FiltroExecucao.montar(
                conjuntoId, etapa, status, dagRunId, arquivoId, competencia,
                fonteSigla, tipo, automatica, comFalha, desde, ate, q,
                pagina, tamanho, ordenar));
    }

    @GetMapping("/{execucaoId}")
    @Operation(
            summary = "Detalha uma execucao",
            description = "A execucao com as etapas irmas da mesma rodada, as versoes que "
                    + "entraram como insumo, as transformacoes registradas, as "
                    + "movimentacoes de zona e as validacoes de qualidade."
    )
    public ExecucaoDetalheResponse detalhar(
            @Parameter(description = "Identificador da execucao")
            @PathVariable Long execucaoId
    ) {
        return servico.detalhar(execucaoId);
    }

    @GetMapping("/pipelines")
    @Operation(
            summary = "Historico das rodadas do pipeline",
            description = "Uma linha por rodada, ou seja por dag_run_id, com a situacao "
                    + "consolidada, a etapa que falhou quando houve falha e a linha do "
                    + "tempo das etapas. Execucao manual sem dag_run_id nao aparece aqui; "
                    + "ela esta na listagem por etapa."
    )
    public PaginaResponse<RodadaPipelineResponse> rodadas(
            @Parameter(description = "Identificador do conjunto de dados")
            @RequestParam(required = false) Long conjuntoId,

            @Parameter(description = "Status de alguma etapa da rodada")
            @RequestParam(required = false) String status,

            @Parameter(description = "Identificador do arquivo bruto")
            @RequestParam(required = false) Long arquivoId,

            @Parameter(description = "Competencia do arquivo, no formato AAAA-MM")
            @RequestParam(required = false) String competencia,

            @Parameter(description = "Sigla da fonte do dado")
            @RequestParam(required = false) String fonteSigla,

            @Parameter(description = "Codigo do tipo de camada")
            @RequestParam(required = false) String tipo,

            @Parameter(description = "true traz so as rodadas automaticas, false so as manuais")
            @RequestParam(required = false) Boolean automatica,

            @Parameter(description = "true traz so as rodadas que tiveram falha")
            @RequestParam(required = false) Boolean comFalha,

            @Parameter(description = "Data inicial, no formato AAAA-MM-DD")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,

            @Parameter(description = "Data final, incluida por inteiro")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,

            @Parameter(description = "Busca no conjunto, no arquivo, no dagRunId e na mensagem")
            @RequestParam(required = false) String q,

            @Parameter(description = "Pagina, comecando em 0")
            @RequestParam(required = false) Integer pagina,

            @Parameter(description = "Itens por pagina, de 1 a 200. Padrao 20")
            @RequestParam(required = false) Integer tamanho,

            @Parameter(description = "Ordenacao: inicio, duracao, etapa ou conjunto")
            @RequestParam(required = false) String ordenar
    ) {
        return servico.rodadas(FiltroExecucao.montar(
                conjuntoId, null, status, null, arquivoId, competencia,
                fonteSigla, tipo, automatica, comFalha, desde, ate, q,
                pagina, tamanho, ordenar));
    }

    @GetMapping("/pipelines/{dagRunId}")
    @Operation(
            summary = "Uma rodada do pipeline",
            description = "Todas as etapas de um dag_run_id, em ordem, com a situacao "
                    + "consolidada da rodada."
    )
    public RodadaPipelineResponse rodada(
            @Parameter(description = "Identificador da rodada no Airflow")
            @PathVariable String dagRunId
    ) {
        return servico.rodada(dagRunId);
    }

    @GetMapping("/situacao")
    @Operation(
            summary = "Situacao atual do pipeline",
            description = "O que esta em andamento e por quanto tempo, as contagens por "
                    + "status e por etapa na janela, as ultimas falhas, quantas rodadas "
                    + "houve e quando foi a ultima conclusao."
    )
    public SituacaoPipelineResponse situacao(
            @Parameter(description = "Identificador do conjunto de dados")
            @RequestParam(required = false) Long conjuntoId,

            @Parameter(description = "Janela em dias para as contagens, de 1 a 365. Padrao 7")
            @RequestParam(required = false) Integer dias,

            @Parameter(description = "Acima de quantos segundos uma execucao em andamento "
                    + "e marcada como acimaDoLimite. Padrao 3600")
            @RequestParam(required = false) Integer limiteSegundos
    ) {
        return servico.situacao(conjuntoId, dias, limiteSegundos);
    }
}
