package br.com.fatec.georural.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/qualidade")
@RequiredArgsConstructor
@Tag(name = "Qualidade e Quarentena", description = "APIs de consulta paginada somente leitura das execuções do pipeline e registros em quarentena (US#08 - T12)")
public class ConsultaExecucaoQuarentenaController {

    private final ConsultaExecucaoQuarentenaService consultaService;

    @GetMapping("/execucoes")
    @Operation(summary = "Lista as execuções do pipeline de validação/tratamento com contagens de recebidos, aprovados e rejeitados")
    public ResponseEntity<Page<ExecucaoConsultaResponse>> listarExecucoes(
            @Parameter(description = "Identificador opcional do conjunto de dados para filtragem")
            @RequestParam(required = false) Long conjuntoId,
            @PageableDefault(size = 20, sort = "inicio") Pageable pageable) {

        Page<ExecucaoConsultaResponse> resultado = consultaService.listarExecucoes(conjuntoId, pageable);
        return ResponseEntity.ok(resultado);
    }

    @GetMapping("/quarentena")
    @Operation(summary = "Consulta paginada dos registros descartados na zona de quarentena com os motivos e arquivo de origem")
    public ResponseEntity<Page<QuarentenaConsultaResponse>> consultarQuarentena(
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
        return ResponseEntity.ok(resultado);
    }
}