package br.com.fatec.georural.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fatec.georural.dto.response.tratada.ProcessamentoTratadaResponse;
import br.com.fatec.georural.dto.response.tratada.TransformacaoTratadaResponse;
import br.com.fatec.georural.dto.response.tratada.ZonaTratadaResumoResponse;
import br.com.fatec.georural.service.ConsultaZonaTratadaService;

/**
 * Endpoints da tela "Zona tratada" (US09).
 */
@RestController
@RequestMapping("/api/zona-tratada")
public class ZonaTratadaController {

    private final ConsultaZonaTratadaService service;

    public ZonaTratadaController(ConsultaZonaTratadaService service) {
        this.service = service;
    }

    /** Lista as execuções que gravaram na zona tratada. */
    @GetMapping
    public List<ZonaTratadaResumoResponse> listar() {
        return service.listar();
    }

    /** Passos de tratamento de uma execução. */
    @GetMapping("/execucoes/{execucaoId}/transformacoes")
    public List<TransformacaoTratadaResponse> transformacoes(@PathVariable Long execucaoId) {
        return service.transformacoes(execucaoId);
    }

    /** Informações do processamento de uma execução. */
    @GetMapping("/execucoes/{execucaoId}/processamento")
    public ProcessamentoTratadaResponse processamento(@PathVariable Long execucaoId) {
        return service.processamento(execucaoId);
    }
}
