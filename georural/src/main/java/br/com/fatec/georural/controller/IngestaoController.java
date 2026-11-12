package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.dto.response.ErroResponse;
import br.com.fatec.georural.dto.response.ingestao.EnvioLoteResponse;
import br.com.fatec.georural.service.ingestao.ProcessamentoPosEnvioService;
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
    private final ProcessamentoPosEnvioService processamentoPosEnvio;

    public IngestaoController(IngestaoService service,
                              ProcessamentoPosEnvioService processamentoPosEnvio) {
        this.service = service;
        this.processamentoPosEnvio = processamentoPosEnvio;
    }

    // parametros opcionais no Spring de proposito: a ausencia cai na mensagem do servico
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia arquivos e devolve a lista de resultados",
            description = "Resposta em lista, um item por arquivo. Mantida como estava para nao "
                    + "quebrar a tela de upload atual; para totais, use POST /api/ingestao/lote.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Confira o desfecho de cada arquivo"),
            @ApiResponse(responseCode = "400", description = "Lista vazia",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conjunto nao encontrado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conjunto arquivado ou que nao aceita upload",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<List<ArquivoValidacaoResponse>> receberArquivos(
            @Parameter(description = "Arquivos a enviar; pode ser mais de um")
            @RequestParam(value = "files", required = false) List<MultipartFile> arquivos,
            @Parameter(description = "Conjunto de dados de destino")
            @RequestParam(value = "conjuntoId", required = false) Long conjuntoId,
            @Parameter(description = "Periodo dos dados: 2026, 2026-11 ou 11/2026")
            @RequestParam(value = "competencia", required = false) String competencia,
            @Parameter(description = "Responsavel pelo envio; omitido, usa o do conjunto")
            @RequestParam(value = "usuId", required = false) Long usuId
    ) {
        return ResponseEntity.ok(service.ingerir(arquivos, conjuntoId, competencia, usuId));
    }

    // mesmo envio, com totais e id de correlacao
    @PostMapping(value = "/lote", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia arquivos em lote e devolve o resumo",
            description = "Devolve totais (enviados, aceitos, duplicados, recusados, bytes), "
                    + "duracao, id de correlacao do envio e a lista de itens. Com "
                    + "processarAposEnvio=true, roda o pipeline nos aceitos antes de responder "
                    + "e a resposta espera o processamento terminar.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lote processado",
                    content = @Content(schema = @Schema(implementation = EnvioLoteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Lista vazia",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conjunto nao encontrado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conjunto arquivado ou que nao aceita upload",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<EnvioLoteResponse> receberLote(
            @Parameter(description = "Arquivos a enviar; pode ser mais de um")
            @RequestParam(value = "files", required = false) List<MultipartFile> arquivos,
            @Parameter(description = "Conjunto de dados de destino")
            @RequestParam(value = "conjuntoId", required = false) Long conjuntoId,
            @Parameter(description = "Periodo dos dados: 2026, 2026-11 ou 11/2026")
            @RequestParam(value = "competencia", required = false) String competencia,
            @Parameter(description = "Responsavel pelo envio; omitido, usa o do conjunto")
            @RequestParam(value = "usuId", required = false) Long usuId,
            @Parameter(description = "Roda o pipeline nos arquivos aceitos antes de responder. "
                    + "A requisicao fica aberta ate o processamento terminar.")
            @RequestParam(value = "processarAposEnvio", required = false, defaultValue = "false")
            boolean processarAposEnvio
    ) {
        EnvioLoteResponse envio = service.ingerirLote(arquivos, conjuntoId, competencia, usuId);
        if (processarAposEnvio) {
            envio = envio.comProcessamento(processamentoPosEnvio.rodar(envio));
        }
        return ResponseEntity.ok(envio);
    }
}
