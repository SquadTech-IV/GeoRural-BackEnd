package br.com.fatec.georural.service.pipeline.leitor;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import lombok.RequiredArgsConstructor;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.geojson.feature.FeatureJSON;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;


@Component
@RequiredArgsConstructor
public class LeitorGeoJson implements LeitorFeicoes {

    private final ExtratorMembroZip extrator;

    @Override
    public boolean suporta(FormatoArquivo formato) {
        return formato == FormatoArquivo.GEOJSON || formato == FormatoArquivo.JSON;
    }

    @Override
    public long ler(ContextoLeitura ctx, Consumer<FeicaoLida> consumidor) {
        if (!ctx.dentroDeContainer()) {
            try (InputStream in = ctx.abrir()) {
                return lerFluxo(in, consumidor);
            } catch (IOException e) {
                throw new IllegalStateException("Falha ao ler o GeoJSON: " + e.getMessage(), e);
            }
        }

        ExtratorMembroZip.MembroExtraido membro =
                extrator.extrair(ctx.abrirObjeto(), ctx.caminhoInterno());
        try (InputStream in = Files.newInputStream(membro.arquivoPrincipal())) {
            return lerFluxo(in, consumidor);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Falha ao ler o GeoJSON de dentro do zip: " + e.getMessage(), e);
        } finally {
            membro.apagar();
        }
    }

    private long lerFluxo(InputStream entrada, Consumer<FeicaoLida> consumidor) throws IOException {
        FeatureJSON featureJSON = new FeatureJSON();
        long lidas = 0;
        try (Reader texto = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
             var it = featureJSON.streamFeatureCollection(texto)) {
            while (it.hasNext()) {
                SimpleFeature f = (SimpleFeature) it.next();
                lidas++;
                consumidor.accept(new FeicaoLida(
                        lidas, LeitorShapefile.atributos(f), (Geometry) f.getDefaultGeometry()));
            }
        }
        return lidas;
    }
}
