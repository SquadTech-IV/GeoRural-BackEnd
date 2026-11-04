package br.com.fatec.georural.service.pipeline.leitor;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;


@Slf4j
@Component
@RequiredArgsConstructor
public class LeitorGeoPackage implements LeitorFeicoes {

    private final ExtratorMembroZip extrator;

    @Override
    public boolean suporta(FormatoArquivo formato) {
        return formato == FormatoArquivo.GEOPACKAGE;
    }

    @Override
    public long ler(ContextoLeitura ctx, Consumer<FeicaoLida> consumidor) {
        Path arquivo = null;
        ExtratorMembroZip.MembroExtraido membro = null;
        try {
            if (ctx.dentroDeContainer()) {
                membro = extrator.extrair(ctx.abrirObjeto(), ctx.caminhoInterno());
                arquivo = membro.arquivoPrincipal();
            } else {
                // GeoPackage e um SQLite: precisa de arquivo em disco, nao serve fluxo.
                // Vai para o disco temporario em copia direta, sem passar pela memoria.
                arquivo = Files.createTempFile("georural-gpkg-", ".gpkg");
                try (InputStream in = ctx.abrir()) {
                    Files.copy(in, arquivo, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return lerArquivo(arquivo, consumidor);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao preparar o GeoPackage: " + e.getMessage(), e);
        } finally {
            if (membro != null) {
                membro.apagar();
            } else if (arquivo != null) {
                try { Files.deleteIfExists(arquivo); } catch (IOException ignored) { }
            }
        }
    }

    private long lerArquivo(Path gpkg, Consumer<FeicaoLida> consumidor) {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("dbtype", "geopkg");
        parametros.put("database", gpkg.toAbsolutePath().toString());

        DataStore store = null;
        long lidas = 0;
        try {
            store = DataStoreFinder.getDataStore(parametros);
            if (store == null) {
                throw new IllegalStateException(
                        "Driver de GeoPackage nao encontrado. Acrescente ao pom.xml:\n"
                                + "  <dependency>\n"
                                + "    <groupId>org.geotools</groupId>\n"
                                + "    <artifactId>gt-geopkg</artifactId>\n"
                                + "    <version>31.1</version>\n"
                                + "  </dependency>");
            }
            // Um .gpkg pode ter varias camadas; lemos todas, na ordem.
            String[] camadas = store.getTypeNames();
            log.debug("GeoPackage '{}' com {} camada(s): {}",
                    gpkg.getFileName(), camadas.length, String.join(", ", camadas));
            for (String nomeCamada : camadas) {
                try (SimpleFeatureIterator it =
                             store.getFeatureSource(nomeCamada).getFeatures().features()) {
                    while (it.hasNext()) {
                        SimpleFeature f = it.next();
                        lidas++;
                        consumidor.accept(new FeicaoLida(
                                lidas, LeitorShapefile.atributos(f), (Geometry) f.getDefaultGeometry()));
                    }
                }
            }
            return lidas;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o GeoPackage: " + e.getMessage(), e);
        } finally {
            if (store != null) {
                store.dispose();
            }
        }
    }
}
