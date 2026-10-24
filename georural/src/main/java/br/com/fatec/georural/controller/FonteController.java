package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.cadastro.FonteRequest;
import br.com.fatec.georural.dto.response.cadastro.ConjuntoResponse;
import br.com.fatec.georural.dto.response.cadastro.FonteResponse;
import br.com.fatec.georural.service.ConjuntoService;
import br.com.fatec.georural.service.FonteService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/fontes")
public class FonteController {

    private final FonteService fonteService;
    private final ConjuntoService conjuntoService;

    public FonteController(
        FonteService fonteService,
        ConjuntoService conjuntoService
    ) {
        this.fonteService = fonteService;
        this.conjuntoService = conjuntoService;
    }

    @GetMapping
    public List<FonteResponse> listar() {
        return fonteService.listar();
    }

    @GetMapping("/{id}")
    public FonteResponse detalhar(@PathVariable Long id) {
        return fonteService.detalhar(id);
    }

    @PostMapping
    public ResponseEntity<FonteResponse> criar(
        @RequestBody FonteRequest request
    ) {
        FonteResponse resposta = fonteService.criar(request);

        return ResponseEntity.created(
            URI.create("/api/fontes/" + resposta.id())
        ).body(resposta);
    }

    @PutMapping("/{id}")
    public FonteResponse atualizar(
        @PathVariable Long id,
        @RequestBody FonteRequest request
    ) {
        return fonteService.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(
        @PathVariable Long id
    ) {
        fonteService.desativar(id);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reativar")
    public FonteResponse reativar(@PathVariable Long id) {
        return fonteService.reativar(id);
    }

    @GetMapping("/{id}/conjuntos")
    public List<ConjuntoResponse> listarConjuntos(
        @PathVariable Long id
    ) {
        return conjuntoService.listar(id);
    }
}