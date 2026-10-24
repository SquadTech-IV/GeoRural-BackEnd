package br.com.fatec.georural.controller;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.io.File;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fatec.georural.service.treatment.embargo.EmbargoTreatment;

@RestController
@RequestMapping("/api/teste")
public class TesteController {

    @PostMapping("/embargo")
    public String embargo() {

        EmbargoTreatment tratamento =
                new EmbargoTreatment(
                        new File(
                                "/workspaces/georural/test-files/Area_do_Imovel.shp"));

        tratamento.execute();

        return "OK";
    }

    @RestController
@RequestMapping("/api/teste")
@RequiredArgsConstructor
public class SqlTesteController {

    private final EntityManager entityManager;

    @GetMapping("/execucao")
    public Object execucao() {
        return entityManager.createNativeQuery("""
            SELECT EXE_ID,
                   EXE_ETAPA,
                   EXE_STATUS,
                   EXE_INICIO,
                   EXE_FIM
            FROM EXECUCAO_PIPELINE
            ORDER BY EXE_ID DESC
        """).getResultList();
    }

    @GetMapping("/transformacao")
    public Object transformacao() {
        return entityManager.createNativeQuery("""
            SELECT TRA_ID,
                   EXE_ID,
                   TRA_ORDEM,
                   TRA_TIPO,
                   TRA_DESCRICAO
            FROM TRANSFORMACAO
            ORDER BY TRA_ID DESC
        """).getResultList();
    }

    @GetMapping("/movimentacao")
    public Object movimentacao() {
        return entityManager.createNativeQuery("""
            SELECT MOV_ID,
                   MOV_ZONA,
                   MOV_STATUS,
                   MOV_DATA
            FROM MOVIMENTACAO_ZONA
            ORDER BY MOV_ID DESC
        """).getResultList();
    }

    @GetMapping("/versao")
    public Object versao() {
        return entityManager.createNativeQuery("""
            SELECT VER_ID,
                   VER_SITUACAO,
                   VER_EXE_ID,
                   VER_NUMERO
            FROM VERSAO
            ORDER BY VER_ID DESC
        """).getResultList();
    }
}
};