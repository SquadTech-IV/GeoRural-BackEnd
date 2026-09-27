package br.com.fatec.georural.service.treatment.property_area.state;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.service.treatment.property_area.PropertyArea;

public class GeometryValidationState implements PropertyAreaState {

    @Override
    public void execute(PropertyArea propertyArea) {
        ShapefileDataStore dataStore = null;
        List<SimpleFeature> validFeatures = new ArrayList<>();

        try {
            dataStore = new ShapefileDataStore(
                    propertyArea.getPropertyAreaFile().toURI().toURL());
                    
            dataStore.setCharset(StandardCharsets.UTF_8);
            
            SimpleFeatureSource source = dataStore.getFeatureSource();

            SimpleFeatureType schema = source.getSchema();

        for (AttributeDescriptor attribute : schema.getAttributeDescriptors()) {
            System.out.println(attribute.getLocalName() + " -> " + attribute.getType().getBinding());
        }
            SimpleFeatureCollection collection = source.getFeatures();

            try (SimpleFeatureIterator iterator = collection.features()) {
                while (iterator.hasNext()) {
                    SimpleFeature feature = iterator.next();

                    Geometry geometry = (Geometry) feature.getDefaultGeometry();

                    if (geometry == null || geometry.isEmpty() || !geometry.isValid()) {
                        throw new IllegalStateException(
                                "Geometria inválida no registro: " + feature.getID());
                    }
                    validFeatures.add(feature);
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler o shapefile: " + e.getMessage(), e);
        } finally {
            if (dataStore != null) {
                dataStore.dispose();
            }
        }

        propertyArea.setFeatures(validFeatures); 
        propertyArea.setState(new FieldsValidationState());
        propertyArea.execute();                    
    }
}