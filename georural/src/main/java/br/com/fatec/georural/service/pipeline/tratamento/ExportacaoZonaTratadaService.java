package br.com.fatec.georural.service.pipeline.tratamento;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;
import br.com.fatec.georural.service.storage.ArmazenamentoZonaPort;
import br.com.fatec.georural.service.storage.Zona;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// grava a camada tratada como GeoJSON na zona TRATADA do data lake
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportacaoZonaTratadaService {

    private final JdbcTemplate jdbc;
    private final ArmazenamentoZonaPort armazenamento;
    private final MovimentacaoZonaRepository movimentacaoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Resultado exportar(Long conjuntoId, Long versaoId, Long versaoNumero, String tipoConjunto,
                              Long arquivoBrutoId, Long execucaoId) {
        Consulta consulta = consultaDe(tipoConjunto);
        if (consulta == null) {
            return new Resultado(null, null, 0, "Camada " + tipoConjunto
                    + " nao tem exportacao para a zona TRATADA.");
        }

        Path temporario = arquivoTemporario();
        long[] feicoes = {0};
        String hash;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (OutputStream arquivo = Files.newOutputStream(temporario);
                 DigestOutputStream comHash = new DigestOutputStream(arquivo, digest);
                 BufferedWriter saida = new BufferedWriter(
                         new OutputStreamWriter(comHash, StandardCharsets.UTF_8))) {

                saida.write("{\"type\":\"FeatureCollection\",\"crs\":{\"type\":\"name\","
                        + "\"properties\":{\"name\":\"EPSG:4674\"}},\"features\":[");

                RowCallbackHandler escritor = resultado -> {
                    try {
                        if (feicoes[0] > 0) {
                            saida.write(',');
                        }
                        saida.write(linhaGeoJson(resultado, consulta));
                        feicoes[0]++;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                };
                jdbc.query(consulta.sql(), escritor, versaoId);

                saida.write("]}");
            }
            hash = hexa(digest.digest());

            long tamanho = Files.size(temporario);
            String caminho = String.format("conjuntos/%d/tratados/v%d/exec-%d/%s.geojson",
                    conjuntoId, versaoNumero, execucaoId, tipoConjunto.toLowerCase());

            Map<String, String> metadados = new LinkedHashMap<>();
            metadados.put("hash-sha256", hash);
            metadados.put("con-id", String.valueOf(conjuntoId));
            metadados.put("ver-id", String.valueOf(versaoId));
            metadados.put("feicoes", String.valueOf(feicoes[0]));

            try (InputStream conteudo = Files.newInputStream(temporario)) {
                armazenamento.gravar(Zona.TRATADA, caminho, conteudo, tamanho, metadados);
            }

            registrarMovimentacao(arquivoBrutoId, execucaoId, caminho, hash, feicoes[0]);
            log.info("Zona TRATADA: {} feicao(oes) em {}", feicoes[0], caminho);

            return new Resultado(caminho, hash, feicoes[0],
                    feicoes[0] + " feicao(oes) em " + caminho);

        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao montar o GeoJSON da zona tratada.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM.", e);
        } finally {
            apagar(temporario);
        }
    }

    private String linhaGeoJson(java.sql.ResultSet resultado, Consulta consulta) {
        StringBuilder feicao = new StringBuilder("{\"type\":\"Feature\",\"properties\":{");
        try {
            for (int i = 0; i < consulta.propriedades().length; i++) {
                if (i > 0) {
                    feicao.append(',');
                }
                String nome = consulta.propriedades()[i];
                feicao.append('"').append(nome).append("\":")
                      .append(valorJson(resultado.getString(nome)));
            }
            feicao.append("},\"geometry\":")
                  .append(geometria(resultado.getString("geojson")))
                  .append('}');
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Falha ao ler a linha da camada tratada: "
                    + e.getMessage(), e);
        }
        return feicao.toString();
    }

    private String geometria(String geojson) {
        return geojson == null || geojson.isBlank() ? "null" : geojson;
    }

    private String valorJson(String valor) {
        if (valor == null) {
            return "null";
        }
        StringBuilder texto = new StringBuilder("\"");
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> texto.append("\\\"");
                case '\\' -> texto.append("\\\\");
                case '\n' -> texto.append("\\n");
                case '\r' -> texto.append("\\r");
                case '\t' -> texto.append("\\t");
                default -> {
                    if (c < 0x20) {
                        texto.append(String.format("\\u%04x", (int) c));
                    } else {
                        texto.append(c);
                    }
                }
            }
        }
        return texto.append('"').toString();
    }

    private void registrarMovimentacao(Long arquivoBrutoId, Long execucaoId, String caminho,
                                       String hash, long feicoes) {
        if (arquivoBrutoId == null) {
            return;
        }
        MovimentacaoZona movimentacao = new MovimentacaoZona();
        movimentacao.setArquivoBruto(entityManager.getReference(ArquivoBruto.class, arquivoBrutoId));
        movimentacao.setExecucao(entityManager.getReference(ExecucaoPipeline.class, execucaoId));
        movimentacao.setZona("TRATADA");
        movimentacao.setStatus("CONCLUIDO");
        movimentacao.setObservacao(cortar(feicoes + " feicao(oes) | caminho=" + caminho
                + " | sha256=" + hash, 400));
        movimentacao.setData(LocalDateTime.now());
        movimentacaoRepository.save(movimentacao);
    }

    // uma consulta por camada; o Oracle entrega a geometria ja em GeoJSON
    private Consulta consultaDe(String tipoConjunto) {
        return switch (tipoConjunto) {
            case "IMOVEL" -> new Consulta("""
                    SELECT i.imo_codigo_car AS codigo_car,
                           TO_CHAR(i.imo_area_total) AS area_ha,
                           m.mun_nome AS municipio,
                           SDO_UTIL.TO_GEOJSON(i.imo_geometria) AS geojson
                      FROM IMOVEL_RURAL i
                      LEFT JOIN MUNICIPIO m ON m.mun_id = i.mun_id
                     WHERE i.ver_id = ?
                     ORDER BY i.imo_codigo_car
                    """, new String[] {"codigo_car", "area_ha", "municipio"});

            case "RESERVA_LEGAL" -> new Consulta("""
                    SELECT i.imo_codigo_car AS codigo_car,
                           TO_CHAR(r.rle_area_ha) AS area_ha,
                           SDO_UTIL.TO_GEOJSON(r.rle_geometria) AS geojson
                      FROM RESERVA_LEGAL r
                      JOIN IMOVEL_RURAL i ON i.imo_id = r.imo_id
                     WHERE r.ver_id = ?
                     ORDER BY i.imo_codigo_car
                    """, new String[] {"codigo_car", "area_ha"});

            case "EMBARGO" -> new Consulta("""
                    SELECT b.emb_identificador AS identificador,
                           SDO_UTIL.TO_GEOJSON(b.emb_geometria) AS geojson
                      FROM EMBARGO b
                     WHERE b.ver_id = ?
                     ORDER BY b.emb_identificador
                    """, new String[] {"identificador"});

            default -> null;
        };
    }

    private Path arquivoTemporario() {
        try {
            return Files.createTempFile("georural-tratada-", ".geojson");
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel criar arquivo temporario.", e);
        }
    }

    private void apagar(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException ignorado) {
            // temporario: falha na limpeza nao afeta o resultado
        }
    }

    private String hexa(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            texto.append(String.format("%02x", b));
        }
        return texto.toString();
    }

    private String cortar(String texto, int max) {
        return texto == null || texto.length() <= max ? texto : texto.substring(0, max - 3) + "...";
    }

    private record Consulta(String sql, String[] propriedades) {
    }

    public record Resultado(String caminho, String hash, long feicoes, String mensagem) {

        public boolean exportou() {
            return caminho != null;
        }
    }
}
