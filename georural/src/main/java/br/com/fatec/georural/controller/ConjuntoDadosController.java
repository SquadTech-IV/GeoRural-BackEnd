package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.trajetoria.ConjuntoResumoResponse;
import br.com.fatec.georural.dto.response.trajetoria.ConjuntoTrajetoriaResponse;
import br.com.fatec.georural.service.TrajetoriaConjuntoService;

import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/trajetoria-conjuntos")
public class ConjuntoDadosController {

    private final TrajetoriaConjuntoService trajetoriaService;

    public ConjuntoDadosController(TrajetoriaConjuntoService trajetoriaService) {
        this.trajetoriaService = trajetoriaService;
    }

    @GetMapping("/resumo")
    public List<ConjuntoResumoResponse> listar() {
        return trajetoriaService.listarConjuntos();
    }

    @GetMapping("/{id}/arquivos-trajetoria")
    public ConjuntoTrajetoriaResponse trajetoriaPorArquivo(@PathVariable Long id) {
        return trajetoriaService.consultarTrajetoriaPorArquivo(id);
    }
}
