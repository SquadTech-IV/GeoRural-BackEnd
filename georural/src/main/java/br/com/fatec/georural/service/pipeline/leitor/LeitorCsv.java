package br.com.fatec.georural.service.pipeline.leitor;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;


@Slf4j
@Component
@RequiredArgsConstructor
public class LeitorCsv implements LeitorFeicoes {

    private static final String[] NOMES_LAT = {"lat", "latitude", "y", "lat_dd", "latitude_dd"};
    private static final String[] NOMES_LON = {"lon", "long", "longitude", "x", "lon_dd", "longitude_dd"};

    private final ExtratorMembroZip extrator;

    @Override
    public boolean suporta(FormatoArquivo formato) {
        return formato == FormatoArquivo.CSV;
    }

    @Override
    public long ler(ContextoLeitura ctx, Consumer<FeicaoLida> consumidor) {
        if (ctx.dentroDeContainer()) {
            ExtratorMembroZip.MembroExtraido membro =
                    extrator.extrair(ctx.abrirObjeto(), ctx.caminhoInterno());
            try {
                return lerFluxo(() -> abrirArquivo(membro.arquivoPrincipal()), ctx, consumidor);
            } finally {
                membro.apagar();
            }
        }
        return lerFluxo(ctx.abrirObjeto(), ctx, consumidor);
    }

    private long lerFluxo(Supplier<InputStream> fonte, ContextoLeitura ctx,
                          Consumer<FeicaoLida> consumidor) {
        int srid = ctx.srsEsperado() != null ? ctx.srsEsperado() : 4674;
        GeometryFactory fabrica = new GeometryFactory(new PrecisionModel(), srid);

        try (InputStream in = fonte.get();
             BufferedReader leitor = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String cabecalhoBruto = leitor.readLine();
            if (cabecalhoBruto == null) {
                return 0;
            }
            char separador = detectarSeparador(cabecalhoBruto);
            List<String> colunas = dividir(cabecalhoBruto, separador);

            int iLat = indiceDe(colunas, NOMES_LAT);
            int iLon = indiceDe(colunas, NOMES_LON);
            if (iLat < 0 || iLon < 0) {
                log.info("CSV '{}' sem colunas de latitude/longitude reconheciveis; "
                        + "as feicoes serao lidas sem geometria.", ctx.nome());
            }

            long linha = 0;
            String bruta;
            while ((bruta = proximaLinhaLogica(leitor)) != null) {
                if (bruta.isBlank()) {
                    continue;
                }
                linha++;
                List<String> valores = dividir(bruta, separador);
                Map<String, Object> atributos = new LinkedHashMap<>();
                for (int i = 0; i < colunas.size(); i++) {
                    atributos.put(colunas.get(i), i < valores.size() ? valores.get(i) : null);
                }

                Double lat = iLat >= 0 ? numero(valor(valores, iLat)) : null;
                Double lon = iLon >= 0 ? numero(valor(valores, iLon)) : null;

                consumidor.accept(new FeicaoLida(linha, atributos,
                        (lat != null && lon != null)
                                ? fabrica.createPoint(new Coordinate(lon, lat))
                                : null));
            }
            return linha;

        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o CSV: " + e.getMessage(), e);
        }
    }

    private String proximaLinhaLogica(BufferedReader leitor) throws IOException {
        String primeira = leitor.readLine();
        if (primeira == null) {
            return null;
        }
        StringBuilder acumulada = new StringBuilder(primeira);
        while (aspasAbertas(acumulada)) {
            String continuacao = leitor.readLine();
            if (continuacao == null) {
                break;
            }
            acumulada.append('\n').append(continuacao);
        }
        return acumulada.toString();
    }

    /** Numero impar de aspas = o registro continua na linha seguinte. */
    private boolean aspasAbertas(CharSequence texto) {
        int aspas = 0;
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) == '"') {
                aspas++;
            }
        }
        return aspas % 2 != 0;
    }

    private static InputStream abrirArquivo(Path arquivo) {
        try {
            return Files.newInputStream(arquivo);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao abrir o CSV extraido: " + e.getMessage(), e);
        }
    }

    private char detectarSeparador(String cabecalho) {
        long pontoVirgula = cabecalho.chars().filter(c -> c == ';').count();
        long virgula = cabecalho.chars().filter(c -> c == ',').count();
        return pontoVirgula > virgula ? ';' : ',';
    }

    private List<String> dividir(String linha, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (entreAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else {
                    entreAspas = !entreAspas;
                }
            } else if (c == separador && !entreAspas) {
                campos.add(atual.toString().trim());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString().trim());
        return campos;
    }

    private int indiceDe(List<String> colunas, String[] nomes) {
        for (String nome : nomes) {
            for (int i = 0; i < colunas.size(); i++) {
                String c = colunas.get(i);
                if (c != null && c.trim().toLowerCase(Locale.ROOT).equals(nome)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String valor(List<String> valores, int indice) {
        return indice >= 0 && indice < valores.size() ? valores.get(indice) : null;
    }

    /** Aceita "-25.41" e "-25,41". */
    private Double numero(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(texto.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
