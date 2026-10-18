package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.ingestao.FonteIngestaoResponse;
import br.com.fatec.georural.dto.ingestao.ImportacaoRequest;
import br.com.fatec.georural.dto.ingestao.ImportacaoResponse;
import br.com.fatec.georural.dto.ingestao.IngestaoRecenteResponse;
import br.com.fatec.georural.dto.ingestao.ItemDisponivelResponse;
import br.com.fatec.georural.service.ingestao.AcompanhamentoIngestaoService;
import br.com.fatec.georural.service.ingestao.ImportacaoApiService;
import br.com.fatec.georural.service.ingestao.IngestaoConsultaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ingestao")
@RequiredArgsConstructor
public class IngestaoApiController {

    private final IngestaoConsultaService consultaService;
    private final ImportacaoApiService importacaoService;
    private final AcompanhamentoIngestaoService acompanhamentoService;

    @GetMapping("/fontes")
    public List<FonteIngestaoResponse> listarFontes() {
        return consultaService.listarFontesComApi();
    }

    @GetMapping("/conjuntos/{conId}/disponiveis")
    public List<ItemDisponivelResponse> disponiveis(@PathVariable Long conId) {
        return importacaoService.listarDisponiveis(conId);
    }

    @PostMapping("/conjuntos/{conId}/importar")
    public ImportacaoResponse importar(@PathVariable Long conId, @RequestBody ImportacaoRequest requisicao) {
        return importacaoService.importar(conId, requisicao.identificadores());
    }

    @GetMapping("/conjuntos/{conId}/recentes")
    public List<IngestaoRecenteResponse> recentes(@PathVariable Long conId,
                                                  @RequestParam(defaultValue = "10") int limite) {
        return acompanhamentoService.recentes(conId, limite);
    }
}