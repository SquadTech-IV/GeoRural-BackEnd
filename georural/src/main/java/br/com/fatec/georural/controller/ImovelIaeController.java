package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.imovel.ImovelGeoJsonResponse;
import br.com.fatec.georural.dto.response.imovel.ImovelListaResponse;
import br.com.fatec.georural.service.ImovelIaeGeoService;
import br.com.fatec.georural.service.ImovelListaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/imoveis")
public class ImovelIaeController {

    private final ImovelIaeGeoService geoService;
    private final ImovelListaService listaService;

    public ImovelIaeController(ImovelIaeGeoService geoService,
                               ImovelListaService listaService) {
        this.geoService = geoService;
        this.listaService = listaService;
    }
    
    @GetMapping
    public List<ImovelListaResponse> listar() {
        return listaService.listar();
    }

    @GetMapping("/{car}/iae")
    public ImovelGeoJsonResponse consultar(@PathVariable String car) {
        return geoService.consultarPorCar(car);
    }
}