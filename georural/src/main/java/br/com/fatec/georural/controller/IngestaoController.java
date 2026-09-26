package br.com.fatec.georural.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/ingestao")
public class IngestaoController {

    @PostMapping("/upload")
    public ResponseEntity<String> receberArquivos(@RequestParam("files") List<MultipartFile> arquivos) {
        return ResponseEntity.ok(arquivos.size() + " arquivo(s) recebido(s) com sucesso.");
    }
}