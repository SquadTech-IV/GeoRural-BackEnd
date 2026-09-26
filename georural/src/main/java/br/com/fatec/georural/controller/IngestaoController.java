package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.service.IdentificadorFormatoService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/ingestao")
public class IngestaoController {

    @Autowired
    private IdentificadorFormatoService identificadorFormatoService;

    @PostMapping("/upload")
    public ResponseEntity<List<ArquivoValidacaoResponse>> receberArquivos(
            @RequestParam("files") List<MultipartFile> arquivos) {

        List<ArquivoValidacaoResponse> resultados = arquivos.stream()
                .map(arquivo -> new ArquivoValidacaoResponse(
                        arquivo.getOriginalFilename(),
                        identificadorFormatoService.extrairExtensao(arquivo),
                        identificadorFormatoService.detectarMimeType(arquivo),
                        identificadorFormatoService.isFormatoAceito(arquivo)
                ))
                .toList();

        return ResponseEntity.ok(resultados);
    }
}