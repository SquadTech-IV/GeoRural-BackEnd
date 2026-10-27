package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoBrutoResumoResponse;
import br.com.fatec.georural.dto.response.ErroResponse;
import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.service.storage.ConsultaArquivoBrutoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/arquivos/brutos")
@Tag(name = "Arquivos brutos", description = "Recebimento de arquivos na zona bruta do data lake")
public class ConsultaArquivoBrutoController {

    private final ConsultaArquivoBrutoService service;

    public ConsultaArquivoBrutoController(ConsultaArquivoBrutoService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Lista os arquivos registrados na zona bruta",
            description = "Lista paginada, dos mais recentes para os mais antigos, com hash, responsável e data e "
                    + "hora do recebimento. Inclui registros de qualquer situação e via de entrada (inclusive os "
                    + "do fluxo de importação de embargos, que podem não ter responsável). Não expõe o endereço "
                    + "do objeto no armazenamento. Somente leitura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de arquivos",
                    content = @Content(schema = @Schema(implementation = PaginaResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Parâmetro inválido: página negativa, tamanho fora de 1 a 100, situação "
                            + "desconhecida ou valor não numérico em conId, pagina ou tamanho",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public PaginaResponse<ArquivoBrutoResumoResponse> listar(
            @Parameter(description = "Filtra pelo conjunto de dados")
            @RequestParam(value = "conId", required = false) Long conId,
            @Parameter(description = "Filtra pela situação: RECEBENDO, RECEBIDO, VALIDANDO, APROVADO, REJEITADO, "
                    + "PROCESSADO ou FALHA")
            @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "Número da página, a partir de 0")
            @RequestParam(value = "pagina", defaultValue = "0") int pagina,
            @Parameter(description = "Itens por página, de 1 a 100")
            @RequestParam(value = "tamanho", defaultValue = "20") int tamanho) {
        return service.listar(conId, status, pagina, tamanho);
    }
}