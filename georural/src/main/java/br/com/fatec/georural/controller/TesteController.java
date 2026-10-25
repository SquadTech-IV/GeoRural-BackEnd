package br.com.fatec.georural.controller;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.fatec.georural.service.treatment.embargo.EmbargoTreatment;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

/**
 * Dispara os dois fluxos de tratamento. Só para teste: remova depois.
 *
 * POST /api/teste/embargo?arquivo=/workspaces/georural/test-files/embargo.shp
 * POST /api/teste/imovel?arquivo=/workspaces/georural/test-files/Area_do_Imovel.shp
 */
@RestController
@RequestMapping("/api/teste")
public class TesteController {

    // AJUSTE: arquivo de EMBARGO (o Area_do_Imovel.shp é do fluxo de imóvel)
    private static final String EMBARGO_PADRAO = "/workspaces/georural/test-files/limite_estadual_pr.shp";

    private static final String IMOVEL_PADRAO = "/workspaces/georural/test-files/Area_do_Imovel_Sujo.shp";

    @PostMapping("/embargo")
    public Map<String, Object> embargo(
            @RequestParam(defaultValue = EMBARGO_PADRAO) String arquivo) {

        EmbargoTreatment tratamento = new EmbargoTreatment(new File(arquivo));

        return executar(tratamento::execute, arquivo);
    }

    @PostMapping("/imovel")
    public Map<String, Object> imovel(
            @RequestParam(defaultValue = IMOVEL_PADRAO) String arquivo) {

        PropertyAreaTreatment tratamento = new PropertyAreaTreatment(new File(arquivo));

        return executar(tratamento::execute, arquivo);
    }

    private Map<String, Object> executar(Runnable fluxo, String arquivo) {

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("arquivo", arquivo);

        long inicio = System.currentTimeMillis();

        try {
            fluxo.run();
            resposta.put("status", "OK");
        } catch (Exception e) {
            resposta.put("status", "ERRO");
            resposta.put("erro", e.toString());
            Throwable causa = e.getCause();
            if (causa != null) {
                resposta.put("causa", causa.toString());
            }
        }

        resposta.put("duracaoMs", System.currentTimeMillis() - inicio);
        return resposta;
    }
}