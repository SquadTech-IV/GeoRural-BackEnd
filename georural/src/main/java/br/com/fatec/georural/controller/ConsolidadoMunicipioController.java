package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.indicador.ConsolidadoMunicipiosResponse;
import br.com.fatec.georural.service.indicador.ConsolidadoMunicipioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/indicadores/{sigla}/municipios")
public class ConsolidadoMunicipioController {

    private final ConsolidadoMunicipioService consolidadoMunicipioService;

    public ConsolidadoMunicipioController(
            ConsolidadoMunicipioService consolidadoMunicipioService) {
        this.consolidadoMunicipioService = consolidadoMunicipioService;
    }

    // Todos os municipios de uma vez, sem paginar: a tela compara os 399.
    @GetMapping
    public ConsolidadoMunicipiosResponse porMunicipio(
            @PathVariable String sigla,
            @RequestParam(required = false) Long versaoId,
            @RequestParam(defaultValue = "true") boolean incluirSemDados) {
        return consolidadoMunicipioService.porMunicipio(sigla, versaoId, incluirSemDados);
    }
}
