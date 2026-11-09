package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.indicador.ContagemSituacaoResponse;
import br.com.fatec.georural.dto.response.indicador.FaixaValorResponse;
import br.com.fatec.georural.dto.response.indicador.IndicadorCatalogoResponse;
import br.com.fatec.georural.dto.response.indicador.MetricaIndicadorResponse;
import br.com.fatec.georural.dto.response.indicador.ParametroRegraResponse;
import br.com.fatec.georural.dto.response.indicador.RegraVigenteResponse;
import br.com.fatec.georural.dto.response.indicador.ResultadoImovelResponse;
import br.com.fatec.georural.dto.response.indicador.ResultadoMunicipioResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Consulta dos indicadores, dos resultados e das metricas agregadas (US#17.03).
@Service
@Transactional(readOnly = true)
public class ConsultaIndicadorService {

    private static final Logger log =
            LoggerFactory.getLogger(ConsultaIndicadorService.class);

    private static final int MAXIMO_GRUPOS_METRICA = 1000;

    // Junta resultado, indicador, imovel, municipio, versao e regra.
    private static final String ORIGEM_RESULTADO = """
              FROM RESULTADO_INDICADOR r
              JOIN INDICADOR i          ON i.ind_id = r.ind_id
              JOIN IMOVEL_RURAL im      ON im.imo_id = r.imo_id
              JOIN VERSAO v             ON v.ver_id = r.ver_id
              LEFT JOIN MUNICIPIO m     ON m.mun_id = im.mun_id
              LEFT JOIN REGRA_CALCULO rc ON rc.rca_id = r.rca_id
            """;

    private static final String ORIGEM_MUNICIPIO = """
              FROM RESULTADO_MUNICIPIO rm
              JOIN INDICADOR i ON i.ind_id = rm.ind_id
              JOIN MUNICIPIO m ON m.mun_id = rm.mun_id
              JOIN VERSAO v    ON v.ver_id = rm.ver_id
            """;

    private static final Map<String, String> ORDENS_RESULTADO = Map.of(
            "imovel", "im.imo_codigo_car, i.ind_sigla, r.rin_id",
            "valor", "r.rin_valor_percentual NULLS LAST, r.rin_id",
            "valorDesc", "r.rin_valor_percentual DESC NULLS LAST, r.rin_id",
            "calculo", "r.rin_data_calculo DESC, r.rin_id",
            "municipio", "m.mun_nome NULLS LAST, im.imo_codigo_car, r.rin_id"
    );

    private static final Map<String, String> ORDENS_MUNICIPIO = Map.of(
            "municipio", "m.mun_nome, i.ind_sigla, rm.rmu_id",
            "valor", "rm.rmu_valor_agregado NULLS LAST, rm.rmu_id",
            "valorDesc", "rm.rmu_valor_agregado DESC NULLS LAST, rm.rmu_id",
            "calculo", "rm.rmu_data_calculo DESC, rm.rmu_id"
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ConsultaIndicadorService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // Os indicadores cadastrados, com a regra em vigor e seus parametros.
    public List<IndicadorCatalogoResponse> catalogo() {
        Map<Long, List<ParametroRegraResponse>> parametros = parametrosDasRegrasVigentes();

        return jdbc.query("""
                SELECT i.ind_id,
                       i.ind_sigla,
                       i.ind_nome,
                       i.ind_descricao,
                       i.ind_unidade,
                       r.rca_id,
                       r.rca_versao,
                       r.rca_formula,
                       r.rca_descricao,
                       r.rca_autor,
                       r.rca_data_criacao,
                       (SELECT COUNT(*)
                          FROM RESULTADO_INDICADOR ri
                         WHERE ri.ind_id = i.ind_id) AS resultados
                  FROM INDICADOR i
                  LEFT JOIN REGRA_CALCULO r
                         ON r.rca_id = (SELECT MAX(r2.rca_id)
                                          FROM REGRA_CALCULO r2
                                         WHERE r2.ind_id = i.ind_id
                                           AND r2.rca_vigente = 'S')
                 ORDER BY i.ind_sigla
                """, (rs, linha) -> {
            Long regraId = rs.getObject("rca_id", Long.class);

            RegraVigenteResponse regra = regraId == null
                    ? null
                    : new RegraVigenteResponse(
                            regraId,
                            rs.getString("rca_versao"),
                            rs.getString("rca_formula"),
                            rs.getString("rca_descricao"),
                            rs.getString("rca_autor"),
                            instante(rs.getTimestamp("rca_data_criacao")),
                            parametros.getOrDefault(regraId, List.of())
                    );

            return new IndicadorCatalogoResponse(
                    rs.getLong("ind_id"),
                    rs.getString("ind_sigla"),
                    rs.getString("ind_nome"),
                    rs.getString("ind_descricao"),
                    rs.getString("ind_unidade"),
                    regra,
                    rs.getLong("resultados")
            );
        });
    }

    // Resultados por imovel.
    public PaginaResponse<ResultadoImovelResponse> resultados(
            FiltroIndicador filtro) {
        List<Object> parametros = new ArrayList<>();
        String where = whereResultado(filtro, parametros);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + ORIGEM_RESULTADO + where,
                Long.class,
                parametros.toArray()
        );

        long totalItens = total == null ? 0L : total;

        if (totalItens == 0) {
            return PaginaResponse.de(
                    List.of(), filtro.pagina(), filtro.tamanho(), 0L);
        }

        List<Object> parametrosPagina = new ArrayList<>(parametros);

        parametrosPagina.add((long) filtro.pagina() * filtro.tamanho());
        parametrosPagina.add(filtro.tamanho());

        String sql = """
                SELECT r.rin_id,
                       r.imo_id,
                       im.imo_codigo_car,
                       im.imo_nome,
                       im.mun_id,
                       m.mun_nome,
                       m.mun_uf,
                       i.ind_sigla,
                       i.ind_nome,
                       i.ind_unidade,
                       r.ver_id,
                       v.ver_numero,
                       v.ver_competencia,
                       r.exe_id,
                       r.rca_id,
                       rc.rca_versao,
                       r.rin_valor_percentual,
                       r.rin_valor_hectares,
                       r.rin_valor_absoluto,
                       r.rin_situacao,
                       r.rin_vigente,
                       r.rin_data_calculo,
                """
                + (filtro.incluirDetalhes()
                        ? "       r.rin_detalhes\n"
                        : "       CAST(NULL AS CLOB) AS rin_detalhes\n")
                + ORIGEM_RESULTADO
                + where
                + " ORDER BY " + ORDENS_RESULTADO.get(filtro.ordenar())
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<ResultadoImovelResponse> itens = jdbc.query(
                sql,
                (rs, linha) -> new ResultadoImovelResponse(
                        rs.getLong("rin_id"),
                        rs.getLong("imo_id"),
                        rs.getString("imo_codigo_car"),
                        rs.getString("imo_nome"),
                        rs.getObject("mun_id", Long.class),
                        rs.getString("mun_nome"),
                        rs.getString("mun_uf"),
                        rs.getString("ind_sigla"),
                        rs.getString("ind_nome"),
                        rs.getString("ind_unidade"),
                        rs.getLong("ver_id"),
                        rs.getObject("ver_numero", Integer.class),
                        rs.getString("ver_competencia"),
                        rs.getObject("exe_id", Long.class),
                        rs.getObject("rca_id", Long.class),
                        rs.getString("rca_versao"),
                        rs.getBigDecimal("rin_valor_percentual"),
                        rs.getBigDecimal("rin_valor_hectares"),
                        rs.getBigDecimal("rin_valor_absoluto"),
                        rs.getString("rin_situacao"),
                        "S".equals(rs.getString("rin_vigente")),
                        instante(rs.getTimestamp("rin_data_calculo")),
                        objetoJson(rs.getString("rin_detalhes"))
                ),
                parametrosPagina.toArray()
        );

        return PaginaResponse.de(
                itens, filtro.pagina(), filtro.tamanho(), totalItens);
    }

    // Resultados agregados por municipio.
    public PaginaResponse<ResultadoMunicipioResponse> resultadosPorMunicipio(
            FiltroIndicador filtro) {
        List<Object> parametros = new ArrayList<>();
        String where = whereMunicipio(filtro, parametros);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + ORIGEM_MUNICIPIO + where,
                Long.class,
                parametros.toArray()
        );

        long totalItens = total == null ? 0L : total;

        if (totalItens == 0) {
            return PaginaResponse.de(
                    List.of(), filtro.pagina(), filtro.tamanho(), 0L);
        }

        List<Object> parametrosPagina = new ArrayList<>(parametros);

        parametrosPagina.add((long) filtro.pagina() * filtro.tamanho());
        parametrosPagina.add(filtro.tamanho());

        String sql = """
                SELECT rm.rmu_id,
                       m.mun_id,
                       m.mun_codigo_origem,
                       m.mun_nome,
                       m.mun_uf,
                       i.ind_sigla,
                       i.ind_nome,
                       i.ind_unidade,
                       rm.ver_id,
                       v.ver_numero,
                       v.ver_competencia,
                       rm.rmu_valor_agregado,
                       rm.rmu_situacao,
                       rm.rmu_vigente,
                       rm.rmu_data_calculo,
                """
                + (filtro.incluirDetalhes()
                        ? "       rm.rmu_detalhes\n"
                        : "       CAST(NULL AS CLOB) AS rmu_detalhes\n")
                + ORIGEM_MUNICIPIO
                + where
                + " ORDER BY " + ORDENS_MUNICIPIO.get(filtro.ordenar())
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<ResultadoMunicipioResponse> itens = jdbc.query(
                sql,
                (rs, linha) -> new ResultadoMunicipioResponse(
                        rs.getLong("rmu_id"),
                        rs.getLong("mun_id"),
                        rs.getString("mun_codigo_origem"),
                        rs.getString("mun_nome"),
                        rs.getString("mun_uf"),
                        rs.getString("ind_sigla"),
                        rs.getString("ind_nome"),
                        rs.getString("ind_unidade"),
                        rs.getLong("ver_id"),
                        rs.getObject("ver_numero", Integer.class),
                        rs.getString("ver_competencia"),
                        rs.getBigDecimal("rmu_valor_agregado"),
                        rs.getString("rmu_situacao"),
                        "S".equals(rs.getString("rmu_vigente")),
                        instante(rs.getTimestamp("rmu_data_calculo")),
                        objetoJson(rs.getString("rmu_detalhes"))
                ),
                parametrosPagina.toArray()
        );

        return PaginaResponse.de(
                itens, filtro.pagina(), filtro.tamanho(), totalItens);
    }

    // Metricas por indicador e versao.
    public List<MetricaIndicadorResponse> metricas(FiltroIndicador filtro) {
        List<Object> parametros = new ArrayList<>();
        String where = whereResultado(filtro, parametros);

        List<Object> parametrosSituacao = new ArrayList<>(parametros);

        Map<String, List<ContagemSituacaoResponse>> situacoes =
                situacoesPorGrupo(where, parametrosSituacao);

        return jdbc.query("""
                SELECT i.ind_sigla,
                       i.ind_nome,
                       i.ind_unidade,
                       r.ver_id,
                       v.ver_numero,
                       v.ver_competencia,
                       COUNT(*) AS resultados,
                       COUNT(DISTINCT r.imo_id) AS imoveis,
                       MIN(r.rin_valor_percentual) AS pct_min,
                       MAX(r.rin_valor_percentual) AS pct_max,
                       AVG(r.rin_valor_percentual) AS pct_media,
                       MEDIAN(r.rin_valor_percentual) AS pct_mediana,
                       MIN(r.rin_valor_hectares) AS ha_min,
                       MAX(r.rin_valor_hectares) AS ha_max,
                       AVG(r.rin_valor_hectares) AS ha_media,
                       SUM(r.rin_valor_hectares) AS ha_soma,
                       MAX(r.rin_data_calculo) AS ultimo_calculo
                """
                + ORIGEM_RESULTADO
                + where
                + """
                 GROUP BY i.ind_sigla, i.ind_nome, i.ind_unidade,
                          r.ver_id, v.ver_numero, v.ver_competencia
                 ORDER BY i.ind_sigla, v.ver_numero DESC
                """
                + " FETCH FIRST " + MAXIMO_GRUPOS_METRICA + " ROWS ONLY",
                (rs, linha) -> {
                    String sigla = rs.getString("ind_sigla");
                    long versaoId = rs.getLong("ver_id");

                    return new MetricaIndicadorResponse(
                            sigla,
                            rs.getString("ind_nome"),
                            rs.getString("ind_unidade"),
                            versaoId,
                            rs.getObject("ver_numero", Integer.class),
                            rs.getString("ver_competencia"),
                            rs.getLong("resultados"),
                            rs.getLong("imoveis"),
                            new FaixaValorResponse(
                                    rs.getBigDecimal("pct_min"),
                                    rs.getBigDecimal("pct_max"),
                                    arredondar(rs.getBigDecimal("pct_media")),
                                    arredondar(rs.getBigDecimal("pct_mediana")),
                                    null
                            ),
                            new FaixaValorResponse(
                                    rs.getBigDecimal("ha_min"),
                                    rs.getBigDecimal("ha_max"),
                                    arredondar(rs.getBigDecimal("ha_media")),
                                    null,
                                    rs.getBigDecimal("ha_soma")
                            ),
                            situacoes.getOrDefault(
                                    sigla + "#" + versaoId, List.of()),
                            instante(rs.getTimestamp("ultimo_calculo"))
                    );
                },
                parametros.toArray()
        );
    }

    // Distribuicao por situacao, com a mesma clausula WHERE das metricas.
    private Map<String, List<ContagemSituacaoResponse>> situacoesPorGrupo(
            String where,
            List<Object> parametros) {
        record Contagem(String chave, String situacao, long quantidade) {}

        List<Contagem> brutas = jdbc.query("""
                SELECT i.ind_sigla,
                       r.ver_id,
                       NVL(r.rin_situacao, 'NAO_INFORMADA') AS situacao,
                       COUNT(*) AS quantidade
                """
                + ORIGEM_RESULTADO
                + where
                + """
                 GROUP BY i.ind_sigla, r.ver_id, NVL(r.rin_situacao, 'NAO_INFORMADA')
                 ORDER BY i.ind_sigla, r.ver_id, 3
                """,
                (rs, linha) -> new Contagem(
                        rs.getString("ind_sigla") + "#" + rs.getLong("ver_id"),
                        rs.getString("situacao"),
                        rs.getLong("quantidade")
                ),
                parametros.toArray()
        );

        Map<String, Long> totais = new LinkedHashMap<>();

        for (Contagem contagem : brutas) {
            totais.merge(contagem.chave(), contagem.quantidade(), Long::sum);
        }

        Map<String, List<ContagemSituacaoResponse>> agrupadas =
                new LinkedHashMap<>();

        for (Contagem contagem : brutas) {
            long total = totais.getOrDefault(contagem.chave(), 0L);

            BigDecimal fatia = total == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(contagem.quantidade())
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

            agrupadas.computeIfAbsent(contagem.chave(), chave -> new ArrayList<>())
                    .add(new ContagemSituacaoResponse(
                            contagem.situacao(),
                            contagem.quantidade(),
                            fatia
                    ));
        }

        return agrupadas;
    }

    private Map<Long, List<ParametroRegraResponse>> parametrosDasRegrasVigentes() {
        Map<Long, List<ParametroRegraResponse>> porRegra = new LinkedHashMap<>();

        jdbc.query("""
                SELECT p.rca_id,
                       p.prc_nome,
                       p.prc_rotulo,
                       p.prc_tipo,
                       p.prc_unidade,
                       p.prc_valor,
                       p.prc_editavel,
                       p.prc_min,
                       p.prc_max
                  FROM PARAMETRO_REGRA_CALCULO p
                 WHERE p.rca_id IN (SELECT MAX(r.rca_id)
                                      FROM REGRA_CALCULO r
                                     WHERE r.rca_vigente = 'S'
                                     GROUP BY r.ind_id)
                 ORDER BY p.rca_id, p.prc_nome
                """, rs -> {
            porRegra.computeIfAbsent(rs.getLong("rca_id"), chave -> new ArrayList<>())
                    .add(new ParametroRegraResponse(
                            rs.getString("prc_nome"),
                            rs.getString("prc_rotulo"),
                            rs.getString("prc_tipo"),
                            rs.getString("prc_unidade"),
                            rs.getString("prc_valor"),
                            "S".equals(rs.getString("prc_editavel")),
                            rs.getBigDecimal("prc_min"),
                            rs.getBigDecimal("prc_max")
                    ));
        });

        return porRegra;
    }

    private String whereResultado(
            FiltroIndicador filtro,
            List<Object> parametros) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        aplicarSiglas(filtro, where, parametros);

        if (filtro.versaoId() != null) {
            where.append(" AND r.ver_id = ?");
            parametros.add(filtro.versaoId());
        }

        if (filtro.imovelId() != null) {
            where.append(" AND r.imo_id = ?");
            parametros.add(filtro.imovelId());
        }

        if (filtro.codigoCar() != null) {
            where.append(" AND UPPER(im.imo_codigo_car) = ?");
            parametros.add(filtro.codigoCar());
        }

        if (filtro.municipioId() != null) {
            where.append(" AND im.mun_id = ?");
            parametros.add(filtro.municipioId());
        }

        if (filtro.municipio() != null) {
            where.append(" AND UPPER(NVL(m.mun_nome, ' ')) LIKE ?");
            parametros.add("%" + filtro.municipio() + "%");
        }

        if (filtro.competencia() != null) {
            where.append(" AND v.ver_competencia = ?");
            parametros.add(filtro.competencia());
        }

        if (filtro.situacao() != null) {
            where.append(" AND UPPER(NVL(r.rin_situacao, ' ')) = ?");
            parametros.add(filtro.situacao());
        }

        if (filtro.vigente() != null) {
            where.append(" AND r.rin_vigente = ?");
            parametros.add(filtro.vigente() ? "S" : "N");
        }

        if (filtro.valorMinimo() != null) {
            where.append(" AND r.rin_valor_percentual >= ?");
            parametros.add(filtro.valorMinimo());
        }

        if (filtro.valorMaximo() != null) {
            where.append(" AND r.rin_valor_percentual <= ?");
            parametros.add(filtro.valorMaximo());
        }

        return where.toString();
    }

    private String whereMunicipio(
            FiltroIndicador filtro,
            List<Object> parametros) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        aplicarSiglas(filtro, where, parametros);

        if (filtro.versaoId() != null) {
            where.append(" AND rm.ver_id = ?");
            parametros.add(filtro.versaoId());
        }

        if (filtro.municipioId() != null) {
            where.append(" AND rm.mun_id = ?");
            parametros.add(filtro.municipioId());
        }

        if (filtro.municipio() != null) {
            where.append(" AND UPPER(m.mun_nome) LIKE ?");
            parametros.add("%" + filtro.municipio() + "%");
        }

        if (filtro.competencia() != null) {
            where.append(" AND v.ver_competencia = ?");
            parametros.add(filtro.competencia());
        }

        if (filtro.situacao() != null) {
            where.append(" AND UPPER(NVL(rm.rmu_situacao, ' ')) = ?");
            parametros.add(filtro.situacao());
        }

        if (filtro.vigente() != null) {
            where.append(" AND rm.rmu_vigente = ?");
            parametros.add(filtro.vigente() ? "S" : "N");
        }

        if (filtro.valorMinimo() != null) {
            where.append(" AND rm.rmu_valor_agregado >= ?");
            parametros.add(filtro.valorMinimo());
        }

        if (filtro.valorMaximo() != null) {
            where.append(" AND rm.rmu_valor_agregado <= ?");
            parametros.add(filtro.valorMaximo());
        }

        return where.toString();
    }

    // Lista de siglas vira uma clausula IN com uma marca por sigla.
    private void aplicarSiglas(
            FiltroIndicador filtro,
            StringBuilder where,
            List<Object> parametros) {
        List<String> siglas = filtro.indicadores();

        if (siglas.isEmpty()) {
            return;
        }

        where.append(" AND UPPER(i.ind_sigla) IN (");

        for (int i = 0; i < siglas.size(); i++) {
            where.append(i == 0 ? "?" : ", ?");
            parametros.add(siglas.get(i));
        }

        where.append(")");
    }

    private BigDecimal arredondar(BigDecimal valor) {
        return valor == null
                ? null
                : valor.setScale(4, RoundingMode.HALF_UP);
    }

    private Object objetoJson(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }

        String texto = bruto.trim();

        if (!texto.startsWith("{") && !texto.startsWith("[")) {
            return texto;
        }

        try {
            return json.readTree(texto);
        } catch (Exception e) {
            log.warn("Campo de detalhes nao e JSON valido", e);

            return texto;
        }
    }

    private LocalDateTime instante(Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}
