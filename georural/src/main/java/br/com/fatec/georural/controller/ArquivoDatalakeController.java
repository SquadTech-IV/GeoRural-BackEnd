package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoDetalheResponse;
import br.com.fatec.georural.dto.response.ArquivoDownload;
import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.dto.response.EnvioProcessamentoResponse;
import br.com.fatec.georural.service.ArquivoDatalakeService;
import br.com.fatec.georural.service.IndicadorAutomaticoService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Arquivos recebidos no data lake (zona BRUTA). Lista, detalha, baixa e dispara o
 * processamento, tudo a partir de ARQUIVO_BRUTO + armazenamento de objetos.
 */
@RestController
@RequestMapping("/api/arquivos")
public class ArquivoDatalakeController {

    private final ArquivoDatalakeService service;
    private final IndicadorAutomaticoService indicadorAutomaticoService;

    public ArquivoDatalakeController(ArquivoDatalakeService service,
                                     IndicadorAutomaticoService indicadorAutomaticoService) {
        this.service = service;
        this.indicadorAutomaticoService = indicadorAutomaticoService;
    }

    @GetMapping
    public List<ArquivoResumoResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String situacao) {
        return service.listar(nome, situacao);
    }

    @GetMapping("/{id}")
    public ArquivoDetalheResponse detalhe(@PathVariable Long id) {
        return service.detalhe(id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> baixar(@PathVariable Long id) {
        ArquivoDownload d = service.baixar(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + d.nomeArquivo() + "\"")
                .contentType(MediaType.parseMediaType(d.contentType()))
                .body(d.conteudo());
    }

    @PostMapping("/{id}/processar")
    public EnvioProcessamentoResponse processar(@PathVariable Long id) {
        // 1) processa (e commita) o arquivo bruto
        EnvioProcessamentoResponse resposta = service.enviarParaProcessamento(id);
        // 2) recalcula automaticamente os indicadores dos imóveis afetados (IRL agora; IAE quando o
        //    fluxo de embargo for validado). Roda após o commit, na mesma requisição.
        indicadorAutomaticoService.recalcularAposProcessamento(id);
        return resposta;
    }
}
