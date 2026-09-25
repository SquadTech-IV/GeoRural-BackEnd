package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoDetalheResponse;
import br.com.fatec.georural.dto.response.ArquivoDownload;
import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.service.ArquivoDatalakeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // no ArquivoDatalakeController
    @org.springframework.web.bind.annotation.PostMapping("/{id}/conteudo")
    public org.springframework.http.ResponseEntity<Void> subirConteudo(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam("arquivo")
            org.springframework.web.multipart.MultipartFile arquivo) {
        service.salvarConteudo(id, arquivo);
        return org.springframework.http.ResponseEntity.ok().build();
    }
}