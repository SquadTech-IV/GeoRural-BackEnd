package br.com.fatec.georural.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fatec.georural.dto.response.ImovelIaeGeoJsonResponse;
import br.com.fatec.georural.service.ImovelIaeService;

@RestController
@RequestMapping("/api/imoveis")
public class ImovelIaeController {

    private final ImovelIaeService service;

    public ImovelIaeController(ImovelIaeService service) {
        this.service = service;
    }

    @GetMapping("/{codigoCar}/iae")
    public ImovelIaeGeoJsonResponse consultarIae(@PathVariable String codigoCar) {
        return service.consultarIae(codigoCar);
    }
}