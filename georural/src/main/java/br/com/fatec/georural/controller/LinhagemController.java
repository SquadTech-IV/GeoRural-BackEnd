package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.linhagem.LinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemResultadoItemResponse;
import br.com.fatec.georural.service.LinhagemService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/linhagem/resultados")
public class LinhagemController {

    private final LinhagemService linhagemService;

    public LinhagemController(LinhagemService linhagemService) {
        this.linhagemService = linhagemService;
    }

    @GetMapping
    public List<LinhagemResultadoItemResponse> listar(@RequestParam(required = false) String indicador,
                                                      @RequestParam(required = false) String busca) {
        return linhagemService.listarPublicados(indicador, busca);
    }

    @GetMapping("/{id}")
    public LinhagemResponse detalhar(@PathVariable Long id) {
        return linhagemService.consultar(id);
    }
}