package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.imovel.ImovelIndicadoresResponse;
import br.com.fatec.georural.service.ImovelIndicadoresService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mapa")
public class MapaController {

    private final ImovelIndicadoresService indicadoresService;

    public MapaController(ImovelIndicadoresService indicadoresService) {
        this.indicadoresService = indicadoresService;
    }

    @GetMapping
    public List<ImovelIndicadoresResponse> mapa() {
        return indicadoresService.listarMapa();
    }
}