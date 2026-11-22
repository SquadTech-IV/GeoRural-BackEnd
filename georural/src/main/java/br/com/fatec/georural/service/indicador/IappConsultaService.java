package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.iapp.IappListaItemResponse;
import br.com.fatec.georural.dto.response.iapp.IappResultadoResponse;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class IappConsultaService {

    private static final Set<String> STATUS_VALIDOS = Set.of(
            "CALCULADO", "SEM_APP", "CAMADA_INDISPONIVEL"
    );

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public PaginaResponse<IappListaItemResponse> listar(
            String status, Long municipioId, String busca, Long versaoId, Pageable pageable) {

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (versaoId != null) {
            where.append(" AND r.ver_id = ? ");
            params.add(versaoId);
        } else {
            where.append(" AND r.iap_vigente = 'S' ");
        }

        if (status != null && !status.isBlank()) {
            String statusNorm = status.trim().toUpperCase();
            if (!STATUS_VALIDOS.contains(statusNorm)) {
                throw new IllegalArgumentException(
                        "Status IAPP invalido: " + status
                                + ". Valores esperados: CALCULADO, SEM_APP ou CAMADA_INDISPONIVEL.");
            }
            where.append(" AND UPPER(r.iap_status) = ? ");
            params.add(statusNorm);
        }

        if (municipioId != null) {
            where.append(" AND i.mun_id = ? ");
            params.add(municipioId);
        }

        if (busca != null && !busca.isBlank()) {
            String termo = "%" + busca.trim().toUpperCase() + "%";
            where.append(" AND (UPPER(i.imo_codigo_car) LIKE ? OR UPPER(NVL(i.imo_nome, '')) LIKE ?) ");
            params.add(termo);
            params.add(termo);
        }

        String fromJoin = " FROM RESULTADO_IAPP r "
                + " JOIN IMOVEL_RURAL i ON i.imo_id = r.imo_id "
                + " LEFT JOIN MUNICIPIO m ON m.mun_id = i.mun_id ";

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + fromJoin + where, Long.class, params.toArray());
        long totalItens = total != null ? total : 0L;

        String orderBy = resolverOrdenacaoLista(pageable);
        int offset = pageable.isPaged() ? (int) pageable.getOffset() : 0;
        int limit = pageable.isPaged() ? pageable.getPageSize() : 20;

        String sql = "SELECT r.iap_id, r.imo_id, i.imo_codigo_car, i.imo_nome, "
                + "       i.mun_id, m.mun_nome, r.ver_id, "
                + "       r.iap_app_total_ha, r.iap_app_vegetada_ha, r.iap_passivo_ha, "
                + "       r.iap_iapp_pct, r.iap_area_imovel_ha, "
                + "       r.iap_status, r.iap_mensagem, r.iap_vigente, r.iap_data_calculo "
                + fromJoin + where + orderBy
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(offset);
        pageParams.add(limit);

        List<IappListaItemResponse> itens = jdbc.query(sql, (rs, rowNum) -> new IappListaItemResponse(
                numero(rs.getObject("iap_id")),
                numero(rs.getObject("imo_id")),
                rs.getString("imo_codigo_car"),
                rs.getString("imo_nome"),
                numero(rs.getObject("mun_id")),
                rs.getString("mun_nome"),
                numero(rs.getObject("ver_id")),
                rs.getBigDecimal("iap_app_total_ha"),
                rs.getBigDecimal("iap_app_vegetada_ha"),
                rs.getBigDecimal("iap_passivo_ha"),
                rs.getBigDecimal("iap_iapp_pct"),
                rs.getBigDecimal("iap_area_imovel_ha"),
                rs.getString("iap_status"),
                rs.getString("iap_mensagem"),
                rs.getString("iap_vigente"),
                dataHora(rs.getTimestamp("iap_data_calculo"))
        ), pageParams.toArray());

        PageImpl<IappListaItemResponse> page = new PageImpl<>(itens, pageable, totalItens);
        return PaginaResponse.de(page);
    }

    @Transactional(readOnly = true)
    public IappResultadoResponse consultarPorImovelId(Long imovelId, Long versaoId, boolean incluirGeoJson) {
        if (imovelId == null) {
            throw new IllegalArgumentException("Identificador do imovel nao pode ser nulo");
        }

        Integer existeImovel = jdbc.queryForObject(
                "SELECT COUNT(*) FROM IMOVEL_RURAL WHERE imo_id = ?", Integer.class, imovelId);
        if (existeImovel == null || existeImovel == 0) {
            throw new RecursoNaoEncontradoException("Imovel nao encontrado: " + imovelId);
        }

        return buscarResultado(
                " AND r.imo_id = ? ",
                imovelId,
                versaoId,
                incluirGeoJson,
                "Nenhum resultado IAPP encontrado para o imovel id=" + imovelId
                        + (versaoId != null ? " na versao " + versaoId : " vigente") + ".");
    }

    @Transactional(readOnly = true)
    public IappResultadoResponse consultarPorCodigoCar(String codigoCar, Long versaoId, boolean incluirGeoJson) {
        if (codigoCar == null || codigoCar.isBlank()) {
            throw new IllegalArgumentException("Codigo do CAR nao pode ser vazio");
        }

        String carLimpo = codigoCar.trim();
        Integer existeImovel = jdbc.queryForObject(
                "SELECT COUNT(*) FROM IMOVEL_RURAL WHERE UPPER(imo_codigo_car) = UPPER(?)",
                Integer.class, carLimpo);
        if (existeImovel == null || existeImovel == 0) {
            throw new RecursoNaoEncontradoException("Imovel nao encontrado para o CAR: " + carLimpo);
        }

        return buscarResultado(
                " AND UPPER(i.imo_codigo_car) = UPPER(?) ",
                carLimpo,
                versaoId,
                incluirGeoJson,
                "Nenhum resultado IAPP encontrado para o CAR " + carLimpo
                        + (versaoId != null ? " na versao " + versaoId : " vigente") + ".");
    }

    private IappResultadoResponse buscarResultado(
            String filtroImovel, Object chaveImovel, Long versaoId,
            boolean incluirGeoJson, String mensagemNaoEncontrado) {

        String colsGeo = incluirGeoJson
                ? ", CASE WHEN r.iap_app_vegetada_geom IS NOT NULL "
                + "       THEN SDO_UTIL.TO_GEOJSON(SDO_CS.TRANSFORM(r.iap_app_vegetada_geom, 4326)) "
                + "       ELSE NULL END AS app_veg_geojson, "
                + "  CASE WHEN r.iap_passivo_geom IS NOT NULL "
                + "       THEN SDO_UTIL.TO_GEOJSON(SDO_CS.TRANSFORM(r.iap_passivo_geom, 4326)) "
                + "       ELSE NULL END AS passivo_geojson "
                : ", CAST(NULL AS CLOB) AS app_veg_geojson, CAST(NULL AS CLOB) AS passivo_geojson ";

        StringBuilder sql = new StringBuilder(
                "SELECT r.iap_id, r.imo_id, i.imo_codigo_car, i.imo_nome, "
                        + "       i.mun_id, m.mun_nome, r.ver_id, v.ver_numero, "
                        + "       r.iap_app_ver_id, r.iap_veg_ver_id, r.exe_id, r.rca_id, rc.rca_versao, "
                        + "       r.iap_app_total_ha, r.iap_app_vegetada_ha, r.iap_passivo_ha, "
                        + "       r.iap_iapp_pct, r.iap_area_imovel_ha, "
                        + "       r.iap_filtro_tipos_app, r.iap_filtro_classes_veg, "
                        + "       r.iap_competencia_app, r.iap_competencia_veg, "
                        + "       r.iap_status, r.iap_mensagem, r.iap_vigente, r.iap_data_calculo "
                        + colsGeo
                        + "  FROM RESULTADO_IAPP r "
                        + "  JOIN IMOVEL_RURAL i ON i.imo_id = r.imo_id "
                        + "  LEFT JOIN MUNICIPIO m ON m.mun_id = i.mun_id "
                        + "  LEFT JOIN VERSAO v ON v.ver_id = r.ver_id "
                        + "  LEFT JOIN REGRA_CALCULO rc ON rc.rca_id = r.rca_id "
                        + " WHERE 1=1 "
                        + filtroImovel);

        List<Object> params = new ArrayList<>();
        params.add(chaveImovel);

        if (versaoId != null) {
            sql.append(" AND r.ver_id = ? ");
            params.add(versaoId);
        } else {
            sql.append(" AND r.iap_vigente = 'S' ");
        }

        sql.append(" ORDER BY r.iap_data_calculo DESC, r.iap_id DESC FETCH FIRST 1 ROWS ONLY");

        List<IappResultadoResponse> achados = jdbc.query(sql.toString(),
                (rs, rowNum) -> mapearResultado(rs), params.toArray());

        if (achados.isEmpty()) {
            throw new RecursoNaoEncontradoException(mensagemNaoEncontrado);
        }
        return achados.get(0);
    }

    private IappResultadoResponse mapearResultado(ResultSet rs) throws SQLException {
        return new IappResultadoResponse(
                numero(rs.getObject("iap_id")),
                numero(rs.getObject("imo_id")),
                rs.getString("imo_codigo_car"),
                rs.getString("imo_nome"),
                numero(rs.getObject("mun_id")),
                rs.getString("mun_nome"),
                numero(rs.getObject("ver_id")),
                inteiro(rs.getObject("ver_numero")),
                numero(rs.getObject("iap_app_ver_id")),
                numero(rs.getObject("iap_veg_ver_id")),
                numero(rs.getObject("exe_id")),
                numero(rs.getObject("rca_id")),
                rs.getString("rca_versao"),
                rs.getBigDecimal("iap_app_total_ha"),
                rs.getBigDecimal("iap_app_vegetada_ha"),
                rs.getBigDecimal("iap_passivo_ha"),
                rs.getBigDecimal("iap_iapp_pct"),
                rs.getBigDecimal("iap_area_imovel_ha"),
                rs.getString("iap_filtro_tipos_app"),
                rs.getString("iap_filtro_classes_veg"),
                rs.getString("iap_competencia_app"),
                rs.getString("iap_competencia_veg"),
                rs.getString("iap_status"),
                rs.getString("iap_mensagem"),
                rs.getString("iap_vigente"),
                dataHora(rs.getTimestamp("iap_data_calculo")),
                clobParaString(rs.getObject("app_veg_geojson")),
                clobParaString(rs.getObject("passivo_geojson"))
        );
    }

    private String resolverOrdenacaoLista(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return " ORDER BY r.iap_data_calculo DESC, r.iap_id DESC ";
        }
        return pageable.getSort().stream()
                .map(order -> {
                    String col = switch (order.getProperty()) {
                        case "iappPct", "pct" -> "r.iap_iapp_pct";
                        case "passivoHa", "passivo" -> "r.iap_passivo_ha";
                        case "appTotalHa", "appTotal" -> "r.iap_app_total_ha";
                        case "appVegetadaHa", "appVegetada" -> "r.iap_app_vegetada_ha";
                        case "codigoCar" -> "i.imo_codigo_car";
                        case "status" -> "r.iap_status";
                        default -> "r.iap_data_calculo";
                    };
                    String dir = order.isAscending() ? "ASC" : "DESC";
                    return col + " " + dir + " NULLS LAST";
                })
                .reduce((a, b) -> a + ", " + b)
                .map(s -> " ORDER BY " + s + " ")
                .orElse(" ORDER BY r.iap_data_calculo DESC, r.iap_id DESC ");
    }

    private static String clobParaString(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof String s) {
            return s;
        }
        if (valor instanceof Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (SQLException e) {
                throw new RuntimeException("Falha ao ler geometria GeoJSON (CLOB) do IAPP", e);
            }
        }
        return valor.toString();
    }

    private static Long numero(Object valor) {
        return valor == null ? null : ((Number) valor).longValue();
    }

    private static Integer inteiro(Object valor) {
        return valor == null ? null : ((Number) valor).intValue();
    }

    private static BigDecimal decimal(Object valor) {
        if (valor == null) {
            return null;
        }
        return valor instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(valor));
    }

    private static LocalDateTime dataHora(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}