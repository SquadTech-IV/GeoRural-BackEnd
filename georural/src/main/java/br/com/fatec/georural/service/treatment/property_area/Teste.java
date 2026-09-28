package br.com.fatec.georural.service.treatment.property_area;

import java.io.File;

import org.springframework.boot.SpringApplication;

import br.com.fatec.georural.GeoruralApplication;


public class Teste {
    public static void main(String[] args) {

        SpringApplication.run(GeoruralApplication.class, args); 

        File arquivoTeste = new File("src/test/resources/Area_do_Imovel/Area_do_Imovel.shp");

        PropertyArea propertyArea = new PropertyArea(arquivoTeste);
        propertyArea.execute();
    }
}