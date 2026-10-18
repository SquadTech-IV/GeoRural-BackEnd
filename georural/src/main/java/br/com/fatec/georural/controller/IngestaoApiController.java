package br.com.fatec.georural.controller.ingestao;

import br.com.fatec.georural.dto.ingestao.FonteIngestaoResponse;
import br.com.fatec.georural.service.ingestao.IngestaoConsultaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ingestao")
@RequiredArgsConstructor
public class IngestaoApiController {

    private final IngestaoConsultaService consultaService;

    /** Fontes cadastradas com os conjuntos que aceitam API */
    @GetMapping("/fontes")
    public List<FonteIngestaoResponse> listarFontes() {
        return consultaService.listarFontesComApi();
    }
}