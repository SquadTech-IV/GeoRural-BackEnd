package br.com.fatec.georural.service.treatment;

import java.io.File;

import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.springframework.stereotype.Component;

@Component
public class DetectorTipoCamada {

    public enum TipoCamada {
        EMBARGO,
        IMOVEL
    }

    public TipoCamada detectar(File shapefile) {
        FileDataStore store = null;
        try {
            store = FileDataStoreFinder.getDataStore(shapefile);
            if (store == null) {
                throw new IllegalArgumentException(
                        "Nao foi possivel abrir o shapefile: " + shapefile.getName());
            }

            SimpleFeatureType schema = store.getSchema();

            boolean temNumTad = false;
            boolean temRecibo = false;

            for (AttributeDescriptor attr : schema.getAttributeDescriptors()) {
                String coluna = attr.getLocalName().toLowerCase();
                if (coluna.equals("num_tad")) {
                    temNumTad = true;
                }
                if (coluna.equals("recibo")) {
                    temRecibo = true;
                }
            }

            if (temNumTad) {
                return TipoCamada.EMBARGO;
            }
            if (temRecibo) {
                return TipoCamada.IMOVEL;
            }

            throw new IllegalArgumentException(
                    "Tipo de dado nao reconhecido no arquivo '" + shapefile.getName()
                            + "'. Esperado shapefile de EMBARGO (coluna num_tad) ou IMOVEL (coluna recibo).");

        } catch (java.io.IOException e) {
            throw new RuntimeException(
                    "Erro ao ler o shapefile para detectar o tipo: " + e.getMessage(), e);
        } finally {
            if (store != null) {
                store.dispose();
            }
        }
    }
}