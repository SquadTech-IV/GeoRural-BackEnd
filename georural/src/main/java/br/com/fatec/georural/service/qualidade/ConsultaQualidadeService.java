package br.com.fatec.georural.service.qualidade;

import br.com.fatec.georural.dto.response.ContagemResponse;
import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.qualidade.ItemQuarentenaResponse;
import br.com.fatec.georural.dto.response.qualidade.RegraQualidadeResponse;
import br.com.fatec.georural.dto.response.qualidade.RelatorioQualidadeDetalheResponse;
import br.com.fatec.georural.dto.response.qualidade.RelatorioQualidadeResponse;
import br.com.fatec.georural.dto.response.qualidade.ResumoConjuntoResponse;
import br.com.fatec.georural.dto.response.qualidade.ResumoQualidadeResponse;
import br.com.fatec.georural.dto.response.qualidade.ViolacaoRegraResponse;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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

@Service
@Transactional(readOnly = true)
public class ConsultaQualidadeService {

    private static final Logger log =
            LoggerFactory.getLogger(ConsultaQualidadeService.class);

    private static final int AMOSTRA_PADRAO = 20;
    private static final int AMOSTRA_MAXIMA = 200;
    private static final int TOPO_REGRAS = 10;

    private static final String COLUNAS_RELATORIO = """
            SELECT v.val_id,
                   v.exe_id,
                   v.arq_bruto_id,
                   a.arq_nome,
                   a.arq_competencia,
                   a.arq_formato,
                   c.con_id,
                   c.con_nome,
                   t.tip_codigo,
                   f.fon_sigla,
                   v.val_total_registros,
                   v.val_registros_validos,
                   v.val_registros_invalidos,
                   v.val_status_final,
                   v.val_data_execucao,
                   (SELECT COUNT(*)
                      FROM QUARENTENA q
                     WHERE q.val_id = v.val_id) AS em_quarentena,
                   (SELECT COUNT(*)
                      FROM QUARENTENA q
                     WHERE q.val_id = v.val_id
                       AND q.qua_situacao = 'PENDENTE') AS quarentena_pendente
            """;

    private static final String ORIGEM_RELATORIO = """
              FROM VALIDACAO v
              JOIN ARQUIVO_BRUTO a  ON a.arq_bruto_id = v.arq_bruto_id
              JOIN CONJUNTO_DADOS c ON c.con_id = a.con_id
              JOIN TIPO_CONJUNTO t  ON t.tip_id = c.tip_id
              JOIN FONTE_DO_DADO f  ON f.fon_id = c.fon_id
            """;

    // A mesma origem, descendo ate a quarentena e a regra violada.
    private static final String ORIGEM_VIOLACAO = """
              FROM QUARENTENA q
              JOIN VALIDACAO v      ON v.val_id = q.val_id
              JOIN ARQUIVO_BRUTO a  ON a.arq_bruto_id = v.arq_bruto_id
              JOIN CONJUNTO_DADOS c ON c.con_id = a.con_id
              JOIN TIPO_CONJUNTO t  ON t.tip_id = c.tip_id
              JOIN FONTE_DO_DADO f  ON f.fon_id = c.fon_id
              LEFT JOIN REGRA_QUALIDADE r ON r.reg_id = q.reg_id
            """;

    private static final Map<String, String> ORDENS = Map.of(
            "data", "v.val_data_execucao DESC, v.val_id DESC",
            "aprovacao", "NVL(v.val_registros_validos, 0)"
                    + " / GREATEST(NVL(v.val_total_registros, 0), 1), v.val_id",
            "invalidos", "NVL(v.val_registros_invalidos, 0) DESC, v.val_id",
            "arquivo", "a.arq_nome, v.val_id"
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ConsultaQualidadeService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // Pagina de relatorios, um por validacao de arquivo.
    public PaginaResponse<RelatorioQualidadeResponse> relatorios(
            FiltroQualidade filtro) {
        List<Object> parametros = new ArrayList<>();
        String where = whereRelatorio(filtro, parametros);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + ORIGEM_RELATORIO + where,
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

        List<RelatorioQualidadeResponse> itens = jdbc.query(
                COLUNAS_RELATORIO
                        + ORIGEM_RELATORIO
                        + where
                        + " ORDER BY " + ORDENS.get(filtro.ordenar())
                        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                relatorio(),
                parametrosPagina.toArray()
        );

        return PaginaResponse.de(
                itens, filtro.pagina(), filtro.tamanho(), totalItens);
    }

    // Um relatorio, com as regras violadas e uma amostra dos registros barrados.
    public RelatorioQualidadeDetalheResponse detalhar(
            Long validacaoId,
            Integer tamanhoAmostra,
            boolean incluirDadoOriginal) {
        if (validacaoId == null || validacaoId <= 0) {
            throw new RecursoNaoEncontradoException(
                    "Relatorio de qualidade nao encontrado: " + validacaoId
            );
        }

        int amostra = tamanhoAmostra == null
                ? AMOSTRA_PADRAO
                : tamanhoAmostra;

        if (amostra < 0 || amostra > AMOSTRA_MAXIMA) {
            throw new RequisicaoInvalidaException(
                    "O tamanho da amostra deve estar entre 0 e "
                            + AMOSTRA_MAXIMA + "."
            );
        }

        List<RelatorioQualidadeResponse> achados = jdbc.query(
                COLUNAS_RELATORIO + ORIGEM_RELATORIO + " WHERE v.val_id = ?",
                relatorio(),
                validacaoId
        );

        if (achados.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Relatorio de qualidade nao encontrado: " + validacaoId
            );
        }

        return new RelatorioQualidadeDetalheResponse(
                achados.get(0),
                regrasVioladas(validacaoId),
                contagem("NVL(q.qua_etapa, 'NAO_INFORMADA')", validacaoId),
                contagem("q.qua_situacao", validacaoId),
                contagem("NVL(r.reg_severidade, 'NAO_INFORMADA')", validacaoId),
                amostra,
                amostra == 0
                        ? List.of()
                        : amostra(validacaoId, amostra, incluirDadoOriginal),
                links(validacaoId, achados.get(0))
        );
    }

    // Qualidade acumulada sob os mesmos filtros da listagem.
    public ResumoQualidadeResponse resumo(FiltroQualidade filtro) {
        List<Object> parametros = new ArrayList<>();
        String where = whereRelatorio(filtro, parametros);

        List<ResumoConjuntoResponse> porConjunto = jdbc.query("""
                SELECT c.con_id,
                       c.con_nome,
                       t.tip_codigo,
                       f.fon_sigla,
                       COUNT(*) AS validacoes,
                       SUM(CASE WHEN v.val_status_final = 'APROVADO'
                                THEN 1 ELSE 0 END) AS aprovadas,
                       SUM(CASE WHEN v.val_status_final = 'REJEITADO'
                                THEN 1 ELSE 0 END) AS rejeitadas,
                       SUM(NVL(v.val_total_registros, 0)) AS registros,
                       SUM(NVL(v.val_registros_validos, 0)) AS validos,
                       SUM(NVL(v.val_registros_invalidos, 0)) AS invalidos,
                       SUM((SELECT COUNT(*)
                              FROM QUARENTENA q
                             WHERE q.val_id = v.val_id)) AS em_quarentena,
                       MAX(v.val_data_execucao) AS ultima
                """
                + ORIGEM_RELATORIO
                + where
                + """
                 GROUP BY c.con_id, c.con_nome, t.tip_codigo, f.fon_sigla
                 ORDER BY c.con_nome
                """,
                (rs, linha) -> {
                    long registros = rs.getLong("registros");
                    long validos = rs.getLong("validos");

                    return new ResumoConjuntoResponse(
                            rs.getLong("con_id"),
                            rs.getString("con_nome"),
                            rs.getString("tip_codigo"),
                            rs.getString("fon_sigla"),
                            rs.getLong("validacoes"),
                            rs.getLong("aprovadas"),
                            rs.getLong("rejeitadas"),
                            registros,
                            validos,
                            rs.getLong("invalidos"),
                            percentual(validos, registros),
                            rs.getLong("em_quarentena"),
                            instante(rs.getTimestamp("ultima"))
                    );
                },
                parametros.toArray()
        );

        long validacoes = 0;
        long aprovadas = 0;
        long rejeitadas = 0;
        long registros = 0;
        long validos = 0;
        long invalidos = 0;
        long quarentena = 0;

        for (ResumoConjuntoResponse item : porConjunto) {
            validacoes += item.validacoes();
            aprovadas += item.aprovadas();
            rejeitadas += item.rejeitadas();
            registros += item.totalRegistros();
            validos += item.registrosValidos();
            invalidos += item.registrosInvalidos();
            quarentena += item.emQuarentena();
        }

        return new ResumoQualidadeResponse(
                validacoes,
                aprovadas,
                rejeitadas,
                registros,
                validos,
                invalidos,
                percentual(validos, registros),
                quarentena,
                porConjunto,
                regrasMaisVioladas(filtro)
        );
    }

    // O catalogo das regras de qualidade, com quantas violacoes cada uma acumulou.
    public List<RegraQualidadeResponse> regras(Boolean apenasAtivas) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.reg_id,
                       r.reg_nome,
                       r.reg_tipo,
                       r.reg_descricao,
                       r.reg_severidade,
                       r.reg_ativa,
                       (SELECT COUNT(*)
                          FROM QUARENTENA q
                         WHERE q.reg_id = r.reg_id) AS violacoes
                  FROM REGRA_QUALIDADE r
                """);

        List<Object> parametros = new ArrayList<>();

        if (apenasAtivas != null) {
            sql.append(" WHERE r.reg_ativa = ?");
            parametros.add(apenasAtivas ? "S" : "N");
        }

        sql.append(" ORDER BY r.reg_tipo, r.reg_nome");

        return jdbc.query(
                sql.toString(),
                (rs, linha) -> new RegraQualidadeResponse(
                        rs.getLong("reg_id"),
                        rs.getString("reg_nome"),
                        rs.getString("reg_tipo"),
                        rs.getString("reg_descricao"),
                        rs.getString("reg_severidade"),
                        "S".equals(rs.getString("reg_ativa")),
                        rs.getLong("violacoes")
                ),
                parametros.toArray()
        );
    }

    private RowMapper<RelatorioQualidadeResponse> relatorio() {
        return (rs, linha) -> {
            long total = rs.getLong("val_total_registros");
            long validos = rs.getLong("val_registros_validos");
            String status = rs.getString("val_status_final");

            return new RelatorioQualidadeResponse(
                    rs.getLong("val_id"),
                    rs.getObject("exe_id", Long.class),
                    rs.getLong("arq_bruto_id"),
                    rs.getString("arq_nome"),
                    rs.getString("arq_competencia"),
                    rs.getString("arq_formato"),
                    rs.getLong("con_id"),
                    rs.getString("con_nome"),
                    rs.getString("tip_codigo"),
                    rs.getString("fon_sigla"),
                    total,
                    validos,
                    rs.getLong("val_registros_invalidos"),
                    percentual(validos, total),
                    status,
                    "APROVADO".equals(status),
                    rs.getLong("em_quarentena"),
                    rs.getLong("quarentena_pendente"),
                    instante(rs.getTimestamp("val_data_execucao"))
            );
        };
    }

    private List<ViolacaoRegraResponse> regrasVioladas(Long validacaoId) {
        return jdbc.query("""
                SELECT q.reg_id,
                       NVL(r.reg_nome, 'SEM REGRA ASSOCIADA') AS regra,
                       r.reg_tipo,
                       r.reg_severidade,
                       COUNT(*) AS quantidade
                  FROM QUARENTENA q
                  LEFT JOIN REGRA_QUALIDADE r ON r.reg_id = q.reg_id
                 WHERE q.val_id = ?
                 GROUP BY q.reg_id, r.reg_nome, r.reg_tipo, r.reg_severidade
                 ORDER BY COUNT(*) DESC, 2
                """,
                (rs, linha) -> new ViolacaoRegraResponse(
                        rs.getObject("reg_id", Long.class),
                        rs.getString("regra"),
                        rs.getString("reg_tipo"),
                        rs.getString("reg_severidade"),
                        rs.getLong("quantidade")
                ),
                validacaoId
        );
    }

    // As regras mais violadas no recorte pedido.
    private List<ViolacaoRegraResponse> regrasMaisVioladas(
            FiltroQualidade filtro) {
        List<Object> parametros = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        aplicarBase(filtro, where, parametros);

        if (filtro.severidade() != null) {
            where.append(" AND r.reg_severidade = ?");
            parametros.add(filtro.severidade());
        }

        return jdbc.query("""
                SELECT q.reg_id,
                       NVL(r.reg_nome, 'SEM REGRA ASSOCIADA') AS regra,
                       r.reg_tipo,
                       r.reg_severidade,
                       COUNT(*) AS quantidade
                """
                + ORIGEM_VIOLACAO
                + where
                + """
                 GROUP BY q.reg_id, r.reg_nome, r.reg_tipo, r.reg_severidade
                 ORDER BY COUNT(*) DESC, 2
                """
                + " FETCH FIRST " + TOPO_REGRAS + " ROWS ONLY",
                (rs, linha) -> new ViolacaoRegraResponse(
                        rs.getObject("reg_id", Long.class),
                        rs.getString("regra"),
                        rs.getString("reg_tipo"),
                        rs.getString("reg_severidade"),
                        rs.getLong("quantidade")
                ),
                parametros.toArray()
        );
    }

    // Quebra da quarentena de uma validacao por uma expressao de agrupamento fixa.
    private List<ContagemResponse> contagem(
            String expressao,
            Long validacaoId) {
        return jdbc.query(
                "SELECT " + expressao + " AS rotulo, COUNT(*) AS quantidade"
                        + " FROM QUARENTENA q"
                        + " LEFT JOIN REGRA_QUALIDADE r ON r.reg_id = q.reg_id"
                        + " WHERE q.val_id = ?"
                        + " GROUP BY " + expressao
                        + " ORDER BY COUNT(*) DESC, 1",
                (rs, linha) -> new ContagemResponse(
                        rs.getString("rotulo"),
                        rs.getLong("quantidade")
                ),
                validacaoId
        );
    }

    private List<ItemQuarentenaResponse> amostra(
            Long validacaoId,
            int tamanho,
            boolean incluirDadoOriginal) {
        String colunaDado = incluirDadoOriginal
                ? "q.qua_dado_original"
                : "CAST(NULL AS CLOB)";

        return jdbc.query(
                "SELECT q.qua_id,"
                        + " r.reg_nome,"
                        + " r.reg_severidade,"
                        + " q.qua_etapa,"
                        + " q.qua_identificador,"
                        + " q.qua_linha_origem,"
                        + " q.qua_coluna_erro,"
                        + " q.qua_valor_encontrado,"
                        + " q.qua_valor_esperado,"
                        + " q.qua_motivo_erro,"
                        + " q.qua_situacao,"
                        + " q.qua_data_registro,"
                        + " q.qua_data_resolucao,"
                        + " " + colunaDado + " AS qua_dado_original"
                        + " FROM QUARENTENA q"
                        + " LEFT JOIN REGRA_QUALIDADE r ON r.reg_id = q.reg_id"
                        + " WHERE q.val_id = ?"
                        + " ORDER BY q.qua_id"
                        + " FETCH FIRST " + tamanho + " ROWS ONLY",
                (rs, linha) -> new ItemQuarentenaResponse(
                        rs.getLong("qua_id"),
                        rs.getString("reg_nome"),
                        rs.getString("reg_severidade"),
                        rs.getString("qua_etapa"),
                        rs.getString("qua_identificador"),
                        rs.getObject("qua_linha_origem", Integer.class),
                        rs.getString("qua_coluna_erro"),
                        rs.getString("qua_valor_encontrado"),
                        rs.getString("qua_valor_esperado"),
                        rs.getString("qua_motivo_erro"),
                        rs.getString("qua_situacao"),
                        instante(rs.getTimestamp("qua_data_registro")),
                        instante(rs.getTimestamp("qua_data_resolucao")),
                        objetoJson(rs.getString("qua_dado_original"))
                ),
                validacaoId
        );
    }

    private Map<String, String> links(
            Long validacaoId,
            RelatorioQualidadeResponse relatorio) {
        Map<String, String> links = new LinkedHashMap<>();

        links.put("self", "/api/v1/qualidade/relatorios/" + validacaoId);
        links.put("conjunto", "/api/v1/catalogo/" + relatorio.conjuntoId());

        if (relatorio.execucaoId() != null) {
            links.put("quarentenaCompleta",
                    "/api/v1/qualidade/quarentena?execucaoId=" + relatorio.execucaoId());
            links.put("avaliacaoDaExecucao",
                    "/api/execucoes/" + relatorio.execucaoId() + "/qualidade");
        }

        return links;
    }

    private String whereRelatorio(
            FiltroQualidade filtro,
            List<Object> parametros) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        aplicarBase(filtro, where, parametros);

        if (filtro.severidade() != null) {
            where.append("""
                       AND EXISTS (SELECT 1
                                     FROM QUARENTENA q2
                                     JOIN REGRA_QUALIDADE r2 ON r2.reg_id = q2.reg_id
                                    WHERE q2.val_id = v.val_id
                                      AND r2.reg_severidade = ?)
                    """);
            parametros.add(filtro.severidade());
        }

        return where.toString();
    }

    // Filtros que valem tanto para a validacao quanto para a quarentena.
    private void aplicarBase(
            FiltroQualidade filtro,
            StringBuilder where,
            List<Object> parametros) {
        if (filtro.conjuntoId() != null) {
            where.append(" AND c.con_id = ?");
            parametros.add(filtro.conjuntoId());
        }

        if (filtro.execucaoId() != null) {
            where.append(" AND v.exe_id = ?");
            parametros.add(filtro.execucaoId());
        }

        if (filtro.arquivoId() != null) {
            where.append(" AND v.arq_bruto_id = ?");
            parametros.add(filtro.arquivoId());
        }

        if (filtro.status() != null) {
            where.append(" AND v.val_status_final = ?");
            parametros.add(filtro.status());
        }

        if (filtro.competencia() != null) {
            where.append(" AND a.arq_competencia = ?");
            parametros.add(filtro.competencia());
        }

        if (filtro.fonteSigla() != null) {
            where.append(" AND UPPER(f.fon_sigla) = ?");
            parametros.add(filtro.fonteSigla());
        }

        if (filtro.tipo() != null) {
            where.append(" AND UPPER(t.tip_codigo) = ?");
            parametros.add(filtro.tipo());
        }

        if (filtro.desde() != null) {
            where.append(" AND v.val_data_execucao >= ?");
            parametros.add(Timestamp.valueOf(filtro.desde().atStartOfDay()));
        }

        // O dia final entra inteiro: compara com o comeco do dia seguinte.
        if (filtro.ate() != null) {
            where.append(" AND v.val_data_execucao < ?");
            parametros.add(Timestamp.valueOf(
                    filtro.ate().plusDays(1).atStartOfDay()));
        }

        if (filtro.texto() != null) {
            where.append(" AND UPPER(a.arq_nome) LIKE ?");
            parametros.add("%" + filtro.texto() + "%");
        }

        if (Boolean.TRUE.equals(filtro.comQuarentena())) {
            where.append(" AND EXISTS (SELECT 1 FROM QUARENTENA q3"
                    + " WHERE q3.val_id = v.val_id)");
        }

        if (Boolean.FALSE.equals(filtro.comQuarentena())) {
            where.append(" AND NOT EXISTS (SELECT 1 FROM QUARENTENA q3"
                    + " WHERE q3.val_id = v.val_id)");
        }
    }

    private BigDecimal percentual(long parte, long total) {
        if (total <= 0) {
            return null;
        }

        return BigDecimal.valueOf(parte)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
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
            log.warn("Dado original da quarentena nao e JSON valido", e);

            return texto;
        }
    }

    private LocalDateTime instante(Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}
