package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.pipeline.ExecucaoPipelineRequest;
import br.com.fatec.georural.dto.response.pipeline.EtapaDisponivelResponse;
import br.com.fatec.georural.dto.response.pipeline.EtapaExecutadaResponse;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.service.pipeline.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineExecutor executor;
    private final CargaLimiteTerritorialService cargaLimite;

    /** O que existe e o que ja esta implementado. */
    @GetMapping("/etapas")
    public List<EtapaDisponivelResponse> etapas() {
        return Arrays.stream(Etapa.values())
                .map(e -> new EtapaDisponivelResponse(
                        e.name(), e.ordem(), e.descricao(), e.automatica(), executor.implementada(e)))
                .toList();
    }

    @PostMapping("/etapas/{etapa}")
    public EtapaExecutadaResponse executarEtapa(@PathVariable String etapa,
                                                 @RequestBody ExecucaoPipelineRequest request) {
        return paraResponse(executor.executarEtapa(converter(etapa), contexto(request)));
    }

    /** Roda as etapas 1 a 5 em sequencia. */
    @PostMapping("/executar")
    public List<EtapaExecutadaResponse> executar(@RequestBody ExecucaoPipelineRequest request) {
        return executor.executarAutomatico(contexto(request)).stream()
                .map(this::paraResponse)
                .toList();
    }

    /** Deriva o limite do PR da malha municipal ja ingerida, sem reiniciar a aplicacao. */
    @PostMapping("/limite-territorial/recarregar")
    public Map<String, Object> recarregarLimite() {
        var limite = cargaLimite.derivarDaMalhaMunicipal();
        return Map.of("id", limite.getId(), "tipo", limite.getTipo(),
                "codigo", limite.getCodigo(), "nome", limite.getNome());
    }

    // ---------------- apoio ----------------

    private Etapa converter(String etapa) {
        try {
            return Etapa.valueOf(etapa.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusExceptionSimples(HttpStatus.BAD_REQUEST,
                    "Etapa desconhecida: '" + etapa + "'. Use uma de " + Arrays.toString(Etapa.values()));
        }
    }

    private ContextoExecucao contexto(ExecucaoPipelineRequest r) {
        if (r == null || r.conjuntoId() == null) {
            throw new ResponseStatusExceptionSimples(HttpStatus.BAD_REQUEST,
                    "Informe o conjuntoId.");
        }
        ContextoExecucao ctx = ContextoExecucao.novo(r.conjuntoId(), r.arquivoBrutoId(), r.usuarioId());
        if (r.dagRunId() != null && !r.dagRunId().isBlank()) {
            ctx.setDagRunId(r.dagRunId());
        }
        ctx.setCompetencia(r.competencia());
        if (r.parametros() != null) {
            r.parametros().forEach(ctx::comParametro);
        }
        return ctx;
    }

    private EtapaExecutadaResponse paraResponse(ExecucaoPipeline e) {
        int ordem;
        try {
            ordem = Etapa.valueOf(e.getEtapa()).ordem();
        } catch (IllegalArgumentException ex) {
            ordem = 9;
        }
        return new EtapaExecutadaResponse(
                e.getId(), e.getEtapa(), ordem, e.getDagRunId(), e.getStatus(),
                valor(e.getRegistrosRecebidos()), valor(e.getRegistrosAprovados()),
                valor(e.getRegistrosRejeitados()), e.getMensagem(),
                e.getVersaoGerada() == null ? null : e.getVersaoGerada().getId(),
                e.getInicio(), e.getFim(), e.getDuracaoSeg());
    }

    private long valor(Long v) {
        return v == null ? 0L : v;
    }

    /** Etapa ainda nao implementada -> 501 */
    @ExceptionHandler(PipelineExecutor.EtapaNaoImplementadaException.class)
    public ResponseEntity<Map<String, String>> naoImplementada(
            PipelineExecutor.EtapaNaoImplementadaException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("erro", e.getMessage()));
    }

    /** Malha municipal ainda nao ingerida -> 409, com a instrucao do que fazer. */
    @ExceptionHandler(CargaLimiteTerritorialService.MalhaMunicipalAusenteException.class)
    public ResponseEntity<Map<String, String>> semMalhaMunicipal(
            CargaLimiteTerritorialService.MalhaMunicipalAusenteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusExceptionSimples.class)
    public ResponseEntity<Map<String, String>> requisicaoInvalida(ResponseStatusExceptionSimples e) {
        return ResponseEntity.status(e.status).body(Map.of("erro", e.getMessage()));
    }

    static class ResponseStatusExceptionSimples extends RuntimeException {
        final HttpStatus status;
        ResponseStatusExceptionSimples(HttpStatus status, String mensagem) {
            super(mensagem);
            this.status = status;
        }
    }
}
