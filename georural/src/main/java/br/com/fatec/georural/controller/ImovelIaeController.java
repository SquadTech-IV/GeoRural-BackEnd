package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.imovel.ImovelGeoJsonResponse;
import br.com.fatec.georural.service.ImovelIaeGeoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/imoveis")
public class ImovelIaeController {

    private final ImovelIaeGeoService service;

    public ImovelIaeController(ImovelIaeGeoService service) {
        this.service = service;
    }

    @GetMapping("/{car}/iae")
    public ImovelGeoJsonResponse consultar(@PathVariable String car) {
        return service.consultarPorCar(car);
    }
}