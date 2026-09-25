package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoDetalheResponse;
import br.com.fatec.georural.dto.response.ArquivoDownload;
import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.dto.response.EnvioProcessamentoResponse;
import br.com.fatec.georural.service.ArquivoDatalakeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/arquivos")
public class ArquivoDatalakeController {

    private final ArquivoDatalakeService service;

    public ArquivoDatalakeController(ArquivoDatalakeService service) {
        this.service = service;
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

    @PostMapping("/{id}/conteudo")
    public ResponseEntity<Void> subirConteudo(
            @PathVariable Long id,
            @RequestParam("arquivo") MultipartFile arquivo) {
        service.salvarConteudo(id, arquivo);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/processar")
    public EnvioProcessamentoResponse processar(@PathVariable Long id) {
        return service.enviarParaProcessamento(id);
    }
}