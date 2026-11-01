package br.com.fatec.georural.service.pipeline.leitor;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.feature.Property;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Shapefile, solto ou dentro de zip. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeitorShapefile implements LeitorFeicoes {

    private final ExtratorMembroZip extrator;

    @Override
    public boolean suporta(FormatoArquivo formato) {
        return formato == FormatoArquivo.SHAPEFILE || formato == FormatoArquivo.SHAPEFILE_ZIP;
    }

    @Override
    public long ler(ContextoLeitura ctx, Consumer<FeicaoLida> consumidor) {
        String caminho = ctx.caminhoInterno();

        // Zip que e um shapefile so, sem membro indicado: acha o .shp sozinho.
        if (caminho == null && ctx.formato() == FormatoArquivo.SHAPEFILE_ZIP) {
            caminho = extrator.primeiroComExtensao(ctx.abrirObjeto(), "shp");
            if (caminho == null) {
                throw new IllegalStateException("Nenhum .shp encontrado em '" + ctx.nome() + "'.");
            }
        }

        if (caminho == null) {
            // .shp solto nao tem como ser lido: faltam .dbf e .shx.
            throw new IllegalStateException(
                    "Shapefile '" + ctx.nome() + "' veio sem os arquivos complementares "
                            + "(.shx/.dbf/.prj). Envie o shapefile compactado em .zip.");
        }

        ExtratorMembroZip.MembroExtraido membro = extrator.extrair(ctx.abrirObjeto(), caminho);
        try {
            return lerArquivo(membro.arquivoPrincipal(), consumidor);
        } finally {
            membro.apagar();
        }
    }

    private long lerArquivo(Path shp, Consumer<FeicaoLida> consumidor) {
        FileDataStore store = null;
        long lidas = 0;
        try {
            store = FileDataStoreFinder.getDataStore(shp.toFile());
            if (store == null) {
                throw new IllegalStateException("Nao foi possivel abrir o shapefile: " + shp.getFileName());
            }
            try (SimpleFeatureIterator it = store.getFeatureSource().getFeatures().features()) {
                while (it.hasNext()) {
                    SimpleFeature f = it.next();
                    lidas++;
                    consumidor.accept(new FeicaoLida(lidas, atributos(f), (Geometry) f.getDefaultGeometry()));
                }
            }
            log.debug("Shapefile '{}': {} feicao(oes) lida(s).", shp.getFileName(), lidas);
            return lidas;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o shapefile: " + e.getMessage(), e);
        } finally {
            if (store != null) {
                store.dispose();
            }
        }
    }

    /** Todos os atributos menos a geometria -- ela vai no campo proprio. */
    static Map<String, Object> atributos(SimpleFeature f) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        Object geometria = f.getDefaultGeometry();
        for (Property p : f.getProperties()) {
            Object valor = p.getValue();
            if (valor == geometria || valor instanceof Geometry) {
                continue;
            }
            mapa.put(p.getName().getLocalPart(), valor);
        }
        return mapa;
    }

}
