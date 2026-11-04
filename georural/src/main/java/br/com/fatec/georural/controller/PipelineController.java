package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.pipeline.ExecucaoPipelineRequest;
import br.com.fatec.georural.dto.request.pipeline.LoteProcessamentoRequest;
import br.com.fatec.georural.dto.response.pipeline.EtapaDisponivelResponse;
import br.com.fatec.georural.dto.response.pipeline.EtapaExecutadaResponse;
import br.com.fatec.georural.dto.response.pipeline.LoteProcessamentoResponse;
import br.com.fatec.georural.dto.response.pipeline.LoteProcessamentoResponse.ArquivoDoLoteResponse;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.service.pipeline.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

// entrada do pipeline
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineExecutor executor;
    private final CargaLimiteTerritorialService cargaLimite;
    private final LoteProcessamentoService lote;

    // o que existe e o que ja esta implementado
    @GetMapping("/etapas")
    public List<EtapaDisponivelResponse> etapas() {
        return Arrays.stream(Etapa.values())
                .map(e -> new EtapaDisponivelResponse(
                        e.name(), e.ordem(), e.descricao(), e.automatica(), executor.implementada(e)))
                .toList();
    }

    // roda UMA etapa
    @PostMapping("/etapas/{etapa}")
    public EtapaExecutadaResponse executarEtapa(@PathVariable String etapa,
                                                 @RequestBody ExecucaoPipelineRequest request) {
        return paraResponse(executor.executarEtapa(converter(etapa), contexto(request)));
    }

    // roda as etapas 1 a 5 em sequencia
    @PostMapping("/executar")
    public List<EtapaExecutadaResponse> executar(@RequestBody ExecucaoPipelineRequest request) {
        return executor.executarAutomatico(contexto(request)).stream()
                .map(this::paraResponse)
                .toList();
    }

    // roda o pipeline para VARIOS arquivos, um de cada vez
    @PostMapping("/lote")
    public LoteProcessamentoResponse lote(@RequestBody LoteProcessamentoRequest request) {
        if (request == null || request.conjuntoId() == null) {
            throw new ResponseStatusExceptionSimples(HttpStatus.BAD_REQUEST, "Informe o conjuntoId.");
        }

        LoteProcessamentoService.Resultado resultado = lote.processar(
                request.conjuntoId(),
                request.usuarioId(),
                request.competencia(),
                request.arquivoBrutoIds(),
                Boolean.TRUE.equals(request.pararNoPrimeiroErro()),
                request.parametros());

        List<ArquivoDoLoteResponse> detalhes = resultado.arquivos().stream()
                .map(a -> new ArquivoDoLoteResponse(
                        a.arquivoBrutoId(), a.nome(), a.dagRunId(), a.situacao(),
                        a.recebidas(), a.aprovadas(), a.rejeitadas(), a.versaoGerada(),
                        a.mensagem(),
                        a.etapas().stream().map(this::paraResponse).toList()))
                .toList();

        String mensagem = resultado.interrupcao() != null
                ? resultado.interrupcao()
                : resultado.arquivos().isEmpty()
                        ? "Nenhum arquivo pendente neste conjunto."
                        : resultado.concluidos() + " arquivo(s) concluido(s)"
                          + (resultado.falhados() > 0 ? " e " + resultado.falhados() + " com falha" : "")
                          + ". As versoes ficam como RASCUNHO ate alguem publicar.";

        return new LoteProcessamentoResponse(
                resultado.arquivos().size(), resultado.concluidos(), resultado.falhados(),
                resultado.recebidas(), resultado.rejeitadas(), mensagem, detalhes);
    }

    // quantos arquivos o lote pegaria agora, sem rodar nada
    @GetMapping("/lote/pendentes")
    public Map<String, Object> pendentes(@RequestParam Long conjuntoId) {
        var alvos = lote.resolverAlvos(conjuntoId, null);
        return Map.of(
                "conjuntoId", conjuntoId,
                "pendentes", alvos.size(),
                "arquivos", alvos.stream()
                        .map(a -> Map.of("id", a.getId(), "nome", a.getNome(),
                                "situacao", String.valueOf(a.getStatus())))
                        .toList());
    }

    // deriva o limite do PR da malha municipal ja ingerida
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
        // CADASTRO e CONSUMO existem no CHECK do banco mas nao no enum das 6 etapas
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

    // etapa ainda nao implementada -> 501, que e a resposta honesta
    @ExceptionHandler(PipelineExecutor.EtapaNaoImplementadaException.class)
    public ResponseEntity<Map<String, String>> naoImplementada(
            PipelineExecutor.EtapaNaoImplementadaException e) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of("erro", e.getMessage()));
    }

    // malha municipal ainda nao ingerida -> 409, com a instrucao do que fazer
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
