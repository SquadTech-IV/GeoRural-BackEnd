package br.com.fatec.georural.service.exportacao;

import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultaExportacaoService {


    private static final int TAMANHO_DO_LOTE = 500;

    private static final List<String> PUBLICADAS = List.of("VIGENTE", "ANTERIOR");

    private final JdbcTemplate jdbc;

    public record Versao(Long id, Integer numero, String competencia, String situacao, String conjunto) {}

    public Versao exigirPublicada(Long versaoId) {
        List<Map<String, Object>> achado = jdbc.queryForList("""
                SELECT v.ver_id, v.ver_numero, v.ver_competencia, v.ver_situacao, c.con_nome
                  FROM VERSAO v
                  JOIN CONJUNTO_DADOS c ON c.con_id = v.con_id
                 WHERE v.ver_id = ?
                """, versaoId);

        if (achado.isEmpty()) {
            throw new RecursoNaoEncontradoException("Versao " + versaoId + " nao encontrada.");
        }
        Map<String, Object> v = achado.get(0);
        String situacao = texto(v.get("VER_SITUACAO"));

        if (!PUBLICADAS.contains(situacao)) {
            throw new RequisicaoInvalidaException(
                    "A versao " + versaoId + " esta em " + situacao + " e nao pode ser exportada. "
                    + "Rascunho ainda pode ser recalculado: o arquivo baixado hoje nao valeria amanha.");
        }
        return new Versao(versaoId,
                v.get("VER_NUMERO") == null ? null : ((Number) v.get("VER_NUMERO")).intValue(),
                texto(v.get("VER_COMPETENCIA")), situacao, texto(v.get("CON_NOME")));
    }

    public long[] contar(Long versaoId, String indicador) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS linhas, COUNT(DISTINCT r.imo_id) AS imoveis
                  FROM RESULTADO_INDICADOR r
                  JOIN INDICADOR ind ON ind.ind_id = r.ind_id
                 WHERE r.ver_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(versaoId);
        if (indicador != null && !indicador.isBlank()) {
            sql.append(" AND UPPER(ind.ind_sigla) = ?");
            params.add(indicador.trim().toUpperCase());
        }
        Map<String, Object> r = jdbc.queryForMap(sql.toString(), params.toArray());
        return new long[]{ ((Number) r.get("LINHAS")).longValue(),
                           ((Number) r.get("IMOVEIS")).longValue() };
    }

    public void paraCadaLinha(Long versaoId, String indicador, boolean comGeometria,
                              Consumer<LinhaResultado> consumidor) {


        String colunaGeometria = comGeometria
                ? ", SDO_UTIL.TO_GEOJSON(SDO_CS.TRANSFORM(i.imo_geometria, 4326)) AS geometria"
                : ", CAST(NULL AS CLOB) AS geometria";


        StringBuilder sql = new StringBuilder("""
                SELECT i.imo_codigo_car, m.mun_nome, m.mun_codigo_origem, i.imo_area_total,
                       ind.ind_sigla, ind.ind_unidade,
                       r.rin_valor_percentual, r.rin_valor_hectares, r.rin_valor_absoluto,
                       r.rin_situacao, r.rin_data_calculo, r.rin_detalhes
                """);
        sql.append(colunaGeometria).append("""
                  FROM RESULTADO_INDICADOR r
                  JOIN IMOVEL_RURAL i ON i.imo_id = r.imo_id
                  JOIN INDICADOR   ind ON ind.ind_id = r.ind_id
                  LEFT JOIN MUNICIPIO m ON m.mun_id = i.mun_id
                 WHERE r.ver_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(versaoId);
        if (indicador != null && !indicador.isBlank()) {
            sql.append(" AND UPPER(ind.ind_sigla) = ?");
            params.add(indicador.trim().toUpperCase());
        }

        sql.append(" ORDER BY i.imo_codigo_car, ind.ind_sigla");

        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql.toString(),
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(TAMANHO_DO_LOTE);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps;
        }, (RowCallbackHandler) rs -> consumidor.accept(montar(rs)));
    }

    private LinhaResultado montar(ResultSet rs) throws SQLException {
        return new LinhaResultado(
                rs.getString("IMO_CODIGO_CAR"),
                rs.getString("MUN_NOME"),
                rs.getString("MUN_CODIGO_ORIGEM"),
                rs.getBigDecimal("IMO_AREA_TOTAL"),
                rs.getString("IND_SIGLA"),
                rs.getString("IND_UNIDADE"),
                rs.getBigDecimal("RIN_VALOR_PERCENTUAL"),
                rs.getBigDecimal("RIN_VALOR_HECTARES"),
                rs.getBigDecimal("RIN_VALOR_ABSOLUTO"),
                rs.getString("RIN_SITUACAO"),
                instante(rs.getTimestamp("RIN_DATA_CALCULO")),
                rs.getString("RIN_DETALHES"),
                rs.getString("GEOMETRIA"));
    }

    private OffsetDateTime instante(Timestamp t) {
        return t == null ? null : t.toInstant().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private String texto(Object o) { return o == null ? null : o.toString(); }
}
