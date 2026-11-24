package br.com.fatec.georural.service.pipeline.leitor;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.feature.Property;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Shapefile, solto ou dentro de zip. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeitorShapefile implements LeitorFeicoes {

    // quanto do .dbf e lido para detectar o encoding
    private static final int AMOSTRA_DBF_BYTES = 256 * 1024;

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
            aplicarCharset(store, shp);
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

    // O .dbf do SICAR vem em UTF-8 sem .cpg; sem .cpg o GeoTools assume ISO-8859-1
    // e acento vira lixo ("Prudentopolis" -> "PrudentA3polis").
    private void aplicarCharset(FileDataStore store, Path shp) {
        if (!(store instanceof ShapefileDataStore loja)) {
            return;
        }
        String base = shp.toString().replaceFirst("(?i)\\.shp$", "");
        if (Files.isRegularFile(Path.of(base + ".cpg")) || Files.isRegularFile(Path.of(base + ".CPG"))) {
            return;
        }
        Charset charset = dbfEhUtf8(Path.of(base + ".dbf"))
                ? StandardCharsets.UTF_8
                : StandardCharsets.ISO_8859_1;
        loja.setCharset(charset);
        log.debug("Shapefile '{}': .dbf sem .cpg, lido como {}.", shp.getFileName(), charset);
    }

    // Em DBF todo dado de registro e texto; so o cabecalho tem bytes binarios.
    // Le uma amostra da area de registros e testa se e UTF-8 valido.
    private boolean dbfEhUtf8(Path dbf) {
        if (!Files.isRegularFile(dbf)) {
            return false;
        }
        try (SeekableByteChannel canal = Files.newByteChannel(dbf)) {
            ByteBuffer cabecalho = ByteBuffer.allocate(12);
            if (canal.read(cabecalho) < 12) {
                return false;
            }
            int inicio = ((cabecalho.get(9) & 0xFF) << 8) | (cabecalho.get(8) & 0xFF);
            if (inicio <= 0 || inicio >= canal.size()) {
                return false;
            }
            canal.position(inicio);
            ByteBuffer amostra = ByteBuffer.allocate(AMOSTRA_DBF_BYTES);
            canal.read(amostra);
            amostra.flip();

            // so ASCII na amostra: os dois encodings dao o mesmo texto, mantem o padrao
            if (!temByteAcentuado(amostra)) {
                return false;
            }

            CharsetDecoder decodificador = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            // endOfInput=false: sequencia cortada no fim da amostra nao conta como erro
            return !decodificador
                    .decode(amostra, CharBuffer.allocate(AMOSTRA_DBF_BYTES), false)
                    .isError();
        } catch (IOException | RuntimeException e) {
            log.debug("Encoding de '{}' nao detectado: {}", dbf.getFileName(), e.getMessage());
            return false;
        }
    }

    private boolean temByteAcentuado(ByteBuffer amostra) {
        for (int i = amostra.position(); i < amostra.limit(); i++) {
            if ((amostra.get(i) & 0x80) != 0) {
                return true;
            }
        }
        return false;
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
