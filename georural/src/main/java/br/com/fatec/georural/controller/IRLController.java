package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.indicador.IRLResponse;
import br.com.fatec.georural.service.CalculoIRLGeoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/indicadores/irl")
public class IRLController {

    private final CalculoIRLGeoService calculoService;

    public IRLController(CalculoIRLGeoService calculoService) {
        this.calculoService = calculoService;
    }

    @PostMapping("/{imovelId}/recalcular")
    public IRLResponse recalcular(@PathVariable Long imovelId) {
        return calculoService.calcularReservaLegal(imovelId);
    }
}