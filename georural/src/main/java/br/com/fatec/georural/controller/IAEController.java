package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.service.CalculoIAEService;
import br.com.fatec.georural.service.ConsultaIAEService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/indicadores/iae")
public class IAEController {

    private final ConsultaIAEService consultaService;
    private final CalculoIAEService calculoService;

    public IAEController(
            ConsultaIAEService consultaService,
            CalculoIAEService calculoService) {
        this.consultaService = consultaService;
        this.calculoService = calculoService;
    }

    @GetMapping("/{imovelId}")
    public IAEResponse consultar(@PathVariable Long imovelId) {
        return consultaService.consultarPorImovel(imovelId);
    }

    @PostMapping("/{imovelId}")
    @ResponseStatus(HttpStatus.CREATED)
    public IAEResponse calcular(@PathVariable Long imovelId, @RequestParam Long versaoEmbargoId) {
        return calculoService.calcularSobreposicaoEmbargo(imovelId, versaoEmbargoId);
    }    
}