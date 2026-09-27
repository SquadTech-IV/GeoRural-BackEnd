package br.com.fatec.georural.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.service.IngestaoService;

@RestController
@RequestMapping("/api/ingestao")
public class IngestaoController {

        private final IngestaoService ingestaoService;

        public IngestaoController(IngestaoService ingestaoService) {  
        this.ingestaoService = ingestaoService;
        }

    @PostMapping("/upload")
    public ResponseEntity<List<ArquivoValidacaoResponse>> receberArquivos(
            @RequestParam("files") List<MultipartFile> arquivos) {
                
        List<ArquivoValidacaoResponse> resultados = arquivos.stream()
        .map(ingestaoService::ingerir)
        .toList();

        return ResponseEntity.ok(resultados);
    }
}