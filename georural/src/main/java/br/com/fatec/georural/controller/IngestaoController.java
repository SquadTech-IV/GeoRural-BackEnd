package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.service.IngestaoService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/ingestao")
public class IngestaoController {

    private final IngestaoService service;

    public IngestaoController(IngestaoService service) {
        this.service = service;
    }

    @PostMapping(
        value = "/upload",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<List<ArquivoValidacaoResponse>> receberArquivos(
        @RequestParam("files") List<MultipartFile> arquivos,
        @RequestParam("conjuntoId") Long conjuntoId
    ) {
        return ResponseEntity.ok(
            service.ingerir(
                arquivos,
                conjuntoId
            )
        );
    }
}