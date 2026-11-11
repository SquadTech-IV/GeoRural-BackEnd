package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.dto.response.ErroResponse;
import br.com.fatec.georural.service.IngestaoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/ingestao")
@Tag(name = "Ingestao por upload", description = "Envio de um ou varios arquivos para a zona bruta")
public class IngestaoController {

    private final IngestaoService service;

    public IngestaoController(IngestaoService service) {
        this.service = service;
    }

    // Os parametros sao opcionais no Spring de proposito, como no
    // RecebimentoArquivoController: a ausencia e tratada no servico, com mensagem
    // propria, em vez de cair no erro generico do Spring.
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Envia um ou varios arquivos para a zona bruta",
            description = "Aceita varios arquivos na mesma chamada e devolve um resultado por "
                    + "arquivo, com o desfecho e o motivo. Um arquivo recusado nao impede os "
                    + "outros de entrarem. Formatos aceitos: .zip com shapefile, .geojson, "
                    + ".gpkg, .csv e .tif/.tiff.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Lote processado; confira o desfecho de cada arquivo na resposta"),
            @ApiResponse(responseCode = "400", description = "Lista vazia ou competencia invalida",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conjunto de dados nao encontrado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conjunto arquivado ou que nao aceita upload",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<List<ArquivoValidacaoResponse>> receberArquivos(
            @Parameter(description = "Arquivos a enviar; pode ser mais de um")
            @RequestParam(value = "files", required = false) List<MultipartFile> arquivos,

            @Parameter(description = "Conjunto de dados de destino")
            @RequestParam(value = "conjuntoId", required = false) Long conjuntoId,

            @Parameter(description = "Periodo a que os dados se referem: 2026, 2026-11, 11/2026. "
                    + "Faz parte da IDENTIDADE do arquivo, junto com o conjunto e o hash. "
                    + "Vale para todos os arquivos desta chamada. Omitido, cada arquivo tem a "
                    + "competencia deduzida do proprio nome e, em ultimo caso, do mes corrente.")
            @RequestParam(value = "competencia", required = false) String competencia,

            @Parameter(description = "Responsavel pelo envio (provisorio, sem autenticacao). "
                    + "Omitido, usa o responsavel cadastrado no conjunto.")
            @RequestParam(value = "usuId", required = false) Long usuId
    ) {
        return ResponseEntity.ok(service.ingerir(arquivos, conjuntoId, competencia, usuId));
    }
}
