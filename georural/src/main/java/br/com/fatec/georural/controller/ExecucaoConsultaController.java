package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.pipeline.ExecucaoResumoResponse;
import br.com.fatec.georural.service.pipeline.ExecucaoConsultaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// o que o pipeline rodou, incluindo o que rodou sozinho pelo Airflow
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class ExecucaoConsultaController {

    private final ExecucaoConsultaService consulta;

    // filtro: TODAS (padrao), AGUARDANDO (versao em rascunho) ou FALHADAS
    @GetMapping("/execucoes")
    public List<ExecucaoResumoResponse> execucoes(
            @RequestParam(required = false) Long conjuntoId,
            @RequestParam(required = false, defaultValue = "TODAS") String filtro,
            @RequestParam(required = false, defaultValue = "50") int limite) {
        return consulta.listar(conjuntoId, filtro, limite);
    }
}
