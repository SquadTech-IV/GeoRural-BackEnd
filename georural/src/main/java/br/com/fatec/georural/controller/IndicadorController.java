package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.indicador.IndicadorRequest;
import br.com.fatec.georural.dto.response.indicador.IndicadorResponse;
import br.com.fatec.georural.service.IndicadorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/indicadores")
public class IndicadorController {

    private final IndicadorService indicadorService;

    public IndicadorController(IndicadorService indicadorService) {
        this.indicadorService = indicadorService;
    }

    @GetMapping
    public List<IndicadorResponse> listar() {
        return indicadorService.listar();
    }

    @GetMapping("/{sigla}")
    public IndicadorResponse buscarPorSigla(@PathVariable String sigla) {
        return indicadorService.buscarPorSigla(sigla);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IndicadorResponse cadastrar(@RequestBody IndicadorRequest request) {
        return indicadorService.cadastrar(request);
    }
}