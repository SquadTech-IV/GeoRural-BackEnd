package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.AlteracaoNomeImovelRequest;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.service.pipeline.inventario.ConsultaProducaoArquivoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/imoveis")
@RequiredArgsConstructor
public class ImovelNomeController {

    private final ConsultaProducaoArquivoService producao;

    // nome digitado para o imovel; fica registrado como origem MANUAL
    @PutMapping("/{codigoCar}/nome")
    public Map<String, Object> nomear(@PathVariable String codigoCar,
                                      @RequestBody AlteracaoNomeImovelRequest corpo) {
        int alteradas = producao.nomearImovel(codigoCar, corpo == null ? null : corpo.nome());

        if (alteradas == 0) {
            throw new RecursoNaoEncontradoException(
                    "Imovel nao encontrado para o CAR " + codigoCar + ".");
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("codigoCar", codigoCar);
        resposta.put("nome", corpo == null ? null : corpo.nome());
        resposta.put("nomeOrigem", corpo == null || corpo.nome() == null
                || corpo.nome().isBlank() ? null : "MANUAL");
        return resposta;
    }
}
