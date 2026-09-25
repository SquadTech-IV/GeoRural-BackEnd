package br.com.fatec.georural.services.treatment.property_area;

import java.io.File;

public class Teste {
    public static void main(String[] args) {
        File arquivoTeste = new File("src/test/resource/Area_do_Imovel/Area_do_Imovel.shp");

        PropertyArea propertyArea = new PropertyArea(arquivoTeste);
        propertyArea.execute(); 
        
    }
}
