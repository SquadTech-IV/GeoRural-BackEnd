package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.cadastro.OrigemArquivoResponse;
import br.com.fatec.georural.service.OrigemArquivoService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/arquivos")
public class OrigemArquivoController {

    private final OrigemArquivoService service;

    public OrigemArquivoController(
        OrigemArquivoService service
    ) {
        this.service = service;
    }

    @GetMapping("/{id}/origem")
    public OrigemArquivoResponse consultar(
        @PathVariable Long id
    ) {
        return service.consultar(id);
    }
}