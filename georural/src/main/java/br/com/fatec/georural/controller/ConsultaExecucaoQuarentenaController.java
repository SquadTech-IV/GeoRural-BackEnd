package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.qualidade.ExecucaoConsultaResponse;
import br.com.fatec.georural.dto.response.qualidade.QuarentenaConsultaResponse;
import br.com.fatec.georural.service.qualidade.ConsultaExecucaoQuarentenaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import br.com.fatec.georural.dto.request.qualidade.AlterarSituacaoQuarentenaRequest;
import br.com.fatec.georural.service.qualidade.QuarentenaSituacaoService;

@RestController
@RequestMapping("/api/v1/qualidade")
@RequiredArgsConstructor
@Tag(name = "Qualidade e quarentena", description = "Consulta paginada somente leitura das execuções do pipeline e dos registros em quarentena (US#08 - T12)")
public class ConsultaExecucaoQuarentenaController {

    private final ConsultaExecucaoQuarentenaService consultaService;
    private final QuarentenaSituacaoService situacaoService;

    @GetMapping("/execucoes")
    @Operation(summary = "Lista as execuções do pipeline de validação/tratamento com contagens de recebidos, aprovados e rejeitados")
    public PaginaResponse<ExecucaoConsultaResponse> listarExecucoes(
            @Parameter(description = "Identificador opcional do conjunto de dados para filtragem")
            @RequestParam(required = false) Long conjuntoId,
            @PageableDefault(size = 20, sort = "inicio") Pageable pageable) {

        Page<ExecucaoConsultaResponse> resultado =
                consultaService.listarExecucoes(conjuntoId, pageable);

        return PaginaResponse.de(resultado);
    }

    @GetMapping("/quarentena")
    @Operation(summary = "Consulta paginada dos registros descartados na zona de quarentena com os motivos e arquivo de origem")
    public PaginaResponse<QuarentenaConsultaResponse> consultarQuarentena(
            @Parameter(description = "Identificador opcional do conjunto de dados para filtragem")
            @RequestParam(required = false) Long conjuntoId,
            @Parameter(description = "Identificador opcional da execução para filtragem")
            @RequestParam(required = false) Long execucaoId,
            @Parameter(description = "Situação opcional do registro na quarentena (ex: PENDENTE)")
            @RequestParam(required = false) String situacao,
            @PageableDefault(size = 20, sort = "dataRegistro") Pageable pageable) {

        Page<QuarentenaConsultaResponse> resultado = consultaService.consultarQuarentena(
                conjuntoId, execucaoId, situacao, pageable
        );

        return PaginaResponse.de(resultado);
    }

    @PatchMapping("/quarentena/{id}/situacao")
    @Operation(summary = "Altera a situação de um registro em quarentena (grava qua_data_resolucao e auditoria; Operador só altera registros dos seus próprios arquivos)")
    public QuarentenaConsultaResponse alterarSituacaoQuarentena(
            @Parameter(description = "Identificador do registro na quarentena (qua_id)")
            @PathVariable Long id,
            @RequestBody AlterarSituacaoQuarentenaRequest request) {

        String situacao = request != null ? request.situacao() : null;
        String observacao = request != null ? request.observacao() : null;
        return situacaoService.alterarSituacao(id, situacao, observacao);
    }
}
