package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ErroResponse;
import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.iapp.IappListaItemResponse;
import br.com.fatec.georural.dto.response.iapp.IappResultadoResponse;
import br.com.fatec.georural.service.indicador.IappConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/iapp")
@RequiredArgsConstructor
@Tag(
        name = "IAPP - APP Vegetada e Passivo Ambiental",
        description = "Consulta do Indicador de Área de Preservação Permanente Preservada (IAPP), "
                + "passivo ambiental em hectares e rastreabilidade por imóvel ou listagem paginada. "
                + "Documentação técnica em docs/features/indicador-iapp.md."
)
public class IappController {

    private final IappConsultaService service;

    @GetMapping
    @Operation(
            summary = "Lista paginada dos resultados do IAPP",
            description = "Retorna resultados do IAPP (vigentes por padrão, ou de uma versaoId específica). "
                    + "Permite filtrar por status (CALCULADO, SEM_APP, CAMADA_INDISPONIVEL), município e código CAR ou nome."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de resultados do IAPP retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetro de filtro inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public PaginaResponse<IappListaItemResponse> listar(
            @Parameter(description = "Status do resultado: CALCULADO, SEM_APP ou CAMADA_INDISPONIVEL",
                    example = "CALCULADO")
            @RequestParam(required = false) String status,
            @Parameter(description = "ID interno do município", example = "4109401")
            @RequestParam(required = false) Long municipioId,
            @Parameter(description = "Busca por código CAR ou nome do imóvel", example = "PR-TESTE-IAPP")
            @RequestParam(required = false) String busca,
            @Parameter(description = "Versão opcional da rodada; quando omitido, retorna os vigentes")
            @RequestParam(required = false) Long versaoId,
            @PageableDefault(size = 20, sort = "dataCalculo") Pageable pageable) {
        return service.listar(status, municipioId, busca, versaoId, pageable);
    }

    @GetMapping("/imoveis/{imovelId}")
    @Operation(
            summary = "Consulta o IAPP de um imóvel por ID",
            description = "Retorna app_total_ha, app_vegetada_ha, passivo_ha, iapp_pct, status, mensagem, "
                    + "rastreabilidade de versões/competências/parâmetros/regra e, opcionalmente, GeoJSON de APP vegetada e passivo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado do IAPP retornado com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Imóvel ou resultado IAPP não encontrado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public IappResultadoResponse detalharPorId(
            @Parameter(description = "Identificador interno do imóvel rural (imo_id)", example = "10")
            @PathVariable Long imovelId,
            @Parameter(description = "Versão opcional da rodada; quando omitido, retorna o resultado vigente")
            @RequestParam(required = false) Long versaoId,
            @Parameter(description = "Inclui GeoJSON em SRID 4326 de APP vegetada e passivo ambiental")
            @RequestParam(defaultValue = "false") boolean incluirGeoJson) {
        return service.consultarPorImovelId(imovelId, versaoId, incluirGeoJson);
    }

    @GetMapping("/imoveis/car/{codigoCar}")
    @Operation(
            summary = "Consulta o IAPP de um imóvel pelo código CAR",
            description = "Mesmo contrato de /imoveis/{imovelId}, localizado pelo código oficial do CAR."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado do IAPP retornado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Código CAR vazio ou inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Imóvel ou resultado IAPP não encontrado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public IappResultadoResponse detalharPorCar(
            @Parameter(description = "Código CAR oficial do imóvel rural", example = "PR-TESTE-IAPP-01")
            @PathVariable String codigoCar,
            @Parameter(description = "Versão opcional da rodada; quando omitido, retorna o resultado vigente")
            @RequestParam(required = false) Long versaoId,
            @Parameter(description = "Inclui GeoJSON em SRID 4326 de APP vegetada e passivo ambiental")
            @RequestParam(defaultValue = "false") boolean incluirGeoJson) {
        return service.consultarPorCodigoCar(codigoCar, versaoId, incluirGeoJson);
    }
}