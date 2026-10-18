package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.cadastro.ConjuntoRequest;
import br.com.fatec.georural.dto.response.cadastro.ConjuntoResponse;
import br.com.fatec.georural.service.ConjuntoService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/conjuntos")
public class ConjuntoController {

    private final ConjuntoService service;

    public ConjuntoController(ConjuntoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConjuntoResponse> listar(
        @RequestParam(required = false) Long fonteId
    ) {
        return service.listar(fonteId);
    }

    @GetMapping("/{id}")
    public ConjuntoResponse detalhar(@PathVariable Long id) {
        return service.detalhar(id);
    }

    @PostMapping
    public ResponseEntity<ConjuntoResponse> criar(
        @RequestBody ConjuntoRequest request
    ) {
        ConjuntoResponse resposta = service.criar(request);

        return ResponseEntity.created(
            URI.create("/api/conjuntos/" + resposta.id())
        ).body(resposta);
    }

    @PutMapping("/{id}")
    public ConjuntoResponse atualizar(
        @PathVariable Long id,
        @RequestBody ConjuntoRequest request
    ) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(
        @PathVariable Long id
    ) {
        service.desativar(id);

        return ResponseEntity.noContent().build();
    }
}