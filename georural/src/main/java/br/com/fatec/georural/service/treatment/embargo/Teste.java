package br.com.fatec.georural.service.treatment.embargo;

import java.io.File;

import org.springframework.boot.SpringApplication;

import br.com.fatec.georural.GeoruralApplication;

public class Teste {
    public static void main(String[] args) {

        SpringApplication.run(GeoruralApplication.class, args);

        File arquivoTeste = new File("src/test/resources/Embargos_Ibama/adm_embargos_ibama_a.shp");

        Embargo embargo = new Embargo(arquivoTeste);
        embargo.execute();
    }
}