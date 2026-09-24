package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.service.ArquivoDatalakeService;
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
}