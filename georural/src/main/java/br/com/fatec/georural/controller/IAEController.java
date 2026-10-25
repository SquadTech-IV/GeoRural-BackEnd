package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.service.CalculoIAEService;
import br.com.fatec.georural.service.ConsultaIAEService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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
    @GetMapping("/{carId}")
    public IAEResponse consultarCar(@PathVariable String carId) {
        return consultaService.consultarPorCAR(carId);
    }

    @PostMapping("/{imovelId}")
    @ResponseStatus(HttpStatus.CREATED)
    public IAEResponse calcular(@PathVariable Long imovelId) {
        return calculoService.calcularSobreposicaoEmbargo(imovelId);
    }

    @GetMapping("/imoveis")
    public List<IAEResponse> listar() {
        return consultaService.listarTodosImoveisIAE();
    }
    
}