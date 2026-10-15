package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.trajetoria.StatusAtualResponse;
import br.com.fatec.georural.dto.response.trajetoria.TrajetoriaResponse;
import br.com.fatec.georural.service.TrajetoriaConjuntoService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conjuntos")
public class ConjuntoDadosController {
    private final TrajetoriaConjuntoService trajetoriaService;

    public ConjuntoDadosController(TrajetoriaConjuntoService trajetoriaService) {
        this.trajetoriaService = trajetoriaService;
    }

    @GetMapping("/{id}/trajetoria")
    public TrajetoriaResponse trajetoria(@PathVariable Long id) {
        return trajetoriaService.consultarTrajetoria(id);
    }
    @GetMapping("/{id}/status-atual")
    public StatusAtualResponse statusAtual(@PathVariable Long id) {
        return trajetoriaService.consultarStatusAtual(id);
    }
}
