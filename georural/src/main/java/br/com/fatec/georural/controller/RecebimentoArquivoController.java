package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoBrutoResponse;
import br.com.fatec.georural.dto.response.ErroResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.service.storage.RecebimentoArquivoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/arquivos/brutos")
@Tag(name = "Arquivos brutos", description = "Recebimento de arquivos na zona bruta do data lake")
public class RecebimentoArquivoController {

    private final RecebimentoArquivoService service;

    public RecebimentoArquivoController(RecebimentoArquivoService service) {
        this.service = service;
    }

    // Os parâmetros são opcionais no Spring de propósito: a ausência de qualquer um deles é tratada aqui e
    // no serviço como requisição inválida (400), em vez de cair no tratador genérico (500).
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Envia um arquivo para a zona bruta",
            description = "Guarda o arquivo exatamente como chegou, calcula o hash SHA-256 e registra quem enviou, "
                    + "quando e para qual conjunto. Os três campos (arquivo, conId e usuId) são obrigatórios. "
                    + "Formatos aceitos: shapefile (.zip contendo .shp, .shx e .dbf), GeoJSON (.geojson), "
                    + "GeoPackage (.gpkg), CSV (.csv) e GeoTIFF (.tif ou .tiff). "
                    + "O usuId é uma identificação provisória: verifica que o usuário existe, mas não autentica "
                    + "o chamador (até a autenticação da US#13).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Arquivo recebido e registrado",
                    content = @Content(schema = @Schema(implementation = ArquivoBrutoResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Requisição inválida: campo obrigatório ausente, formato não aceito, conteúdo "
                            + "incompatível com o formato, conjunto arquivado ou que não aceita upload",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conjunto de dados ou usuário não encontrado",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Já existe um arquivo com o mesmo conteúdo (hash idêntico)",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "500", description = "Erro interno",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public ResponseEntity<ArquivoBrutoResponse> receber(
            @Parameter(description = "Identificador do conjunto de dados de destino")
            @RequestParam(value = "conId", required = false) Long conId,
            @Parameter(description = "Identificador do responsável pelo envio (provisório, sem autenticação)")
            @RequestParam(value = "usuId", required = false) Long usuId,
            @Parameter(description = "Arquivo a ser armazenado")
            @RequestParam(value = "arquivo", required = false) MultipartFile arquivo) throws IOException {

        if (arquivo == null || arquivo.getOriginalFilename() == null || arquivo.getOriginalFilename().isBlank()) {
            throw new RequisicaoInvalidaException("O arquivo é obrigatório.");
        }

        try (InputStream conteudo = arquivo.getInputStream()) {
            ArquivoBruto registro = service.receber(conId, usuId, arquivo.getOriginalFilename(), conteudo);
            return ResponseEntity.status(HttpStatus.CREATED).body(ArquivoBrutoResponse.de(registro));
        }
    }
}
