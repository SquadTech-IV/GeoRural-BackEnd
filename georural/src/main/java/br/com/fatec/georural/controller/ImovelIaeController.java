package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.imovel.ImovelGeoJsonResponse;
import br.com.fatec.georural.dto.response.imovel.ImovelIndicadoresResponse;
import br.com.fatec.georural.dto.response.imovel.ImovelListaResponse;
import br.com.fatec.georural.service.ImovelIaeGeoService;
import br.com.fatec.georural.service.ImovelIndicadoresService;
import br.com.fatec.georural.service.ImovelListaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/imoveis")
public class ImovelIaeController {

    private final ImovelIaeGeoService geoService;
    private final ImovelListaService listaService;
    private final ImovelIndicadoresService indicadoresService;

    public ImovelIaeController(ImovelIaeGeoService geoService,
                               ImovelListaService listaService,
                               ImovelIndicadoresService indicadoresService) {
        this.geoService = geoService;
        this.listaService = listaService;
        this.indicadoresService = indicadoresService;
    }

    @GetMapping
    public List<ImovelListaResponse> listar(
            @RequestParam(required = false) String municipio,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "200") int tamanho) {
        return listaService.listar(municipio, busca, pagina, tamanho);
    }

    // quantos imoveis existem com esse filtro, para a tela montar a paginacao
    @GetMapping("/total")
    public Map<String, Object> total(@RequestParam(required = false) String municipio,
                                     @RequestParam(required = false) String busca) {
        return Map.of("total", listaService.contar(municipio, busca));
    }

    @GetMapping("/{car}")
    public ImovelIndicadoresResponse detalhar(@PathVariable String car) {
        return indicadoresService.detalharPorCar(car);
    }

    @GetMapping("/{car}/iae")
    public ImovelGeoJsonResponse consultar(@PathVariable String car) {
        return geoService.consultarPorCar(car);
    }
}