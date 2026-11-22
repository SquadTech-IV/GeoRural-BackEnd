package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.indicador.IsapResumoResponse;
import br.com.fatec.georural.service.ISAPService;
import br.com.fatec.georural.service.ISAPService.ResultadoLoteISAP;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/indicadores/isap")
@RequiredArgsConstructor
@Tag(name = "ISAP - Áreas Protegidas", description = "Cálculo e consulta do Índice de Sobreposição a Áreas Protegidas (US#24)")
public class ISAPController {

    private final ISAPService isapService;

    @GetMapping
    @Operation(summary = "Lista paginada dos resultados vigentes do ISAP por imóvel")
    public PaginaResponse<IsapResumoResponse> listar(
            @RequestParam(required = false) String situacao,
            @RequestParam(required = false) Long municipioId,
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "dataCalculo") Pageable pageable) {
        return PaginaResponse.de(isapService.listarPaginado(situacao, municipioId, busca, pageable));
    }

    @GetMapping("/{imovelId}")
    @Operation(summary = "Consulta o resultado vigente do ISAP e detalhamento por área protegida de um imóvel")
    public IsapResumoResponse consultarPorImovel(@PathVariable Long imovelId) {
        return isapService.consultarPorImovelId(imovelId);
    }

    @GetMapping("/car/{codigoCar}")
    @Operation(summary = "Consulta o resultado vigente do ISAP pelo código CAR do imóvel")
    public IsapResumoResponse consultarPorCar(@PathVariable String codigoCar) {
        return isapService.consultarPorCodigoCar(codigoCar);
    }

    @GetMapping("/{imovelId}/historico")
    @Operation(summary = "Lista o histórico de versões calculadas do ISAP para um imóvel")
    public List<IsapResumoResponse> listarHistorico(@PathVariable Long imovelId) {
        return isapService.listarHistoricoPorImovelId(imovelId);
    }

    @PostMapping("/{imovelId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Calcula (e opcionalmente publica) o ISAP para um imóvel rural")
    public IsapResumoResponse calcularImovel(
            @PathVariable Long imovelId,
            @RequestParam(defaultValue = "true") boolean publicar) {
        return isapService.calcularPorImovel(imovelId, publicar);
    }

    @PostMapping("/lote")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dispara o cálculo em lote do ISAP para uma versão")
    public ResultadoLoteISAP calcularLote(
            @RequestParam Long versaoId,
            @RequestParam(required = false) Long camadaVersaoId,
            @RequestParam(defaultValue = "TODOS") String escopo,
            @RequestParam(defaultValue = "true") boolean publicar) {
        return isapService.calcularLote(versaoId, camadaVersaoId, escopo, publicar);
    }
}