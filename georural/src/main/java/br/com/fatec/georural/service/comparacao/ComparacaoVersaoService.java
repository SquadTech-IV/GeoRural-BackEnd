package br.com.fatec.georural.service.comparacao;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Diferenca;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.EntradaOrigem;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.ImovelComparado;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Indicador;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.MotivoMudanca;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Parametro;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Regra;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Resumo;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.SituacaoImovel;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Valores;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.VersaoPublicada;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * US#19 - Compara duas versoes publicadas de um mesmo indicador, imovel por imovel,
 * e explica o motivo da mudanca (regra, parametro ou dado de origem).
 */
@Service
public class ComparacaoVersaoService {

    static final int TAMANHO_MAXIMO = 200;

    private static final String ORIGEM_A = """
            (SELECT imo_id AS a_imo, rin_valor_percentual AS a_pct, rin_valor_hectares AS a_ha,
                    rin_valor_absoluto AS a_abs, rin_situacao AS a_sit
               FROM RESULTADO_INDICADOR WHERE ver_id = ? AND ind_id = ?) a
            """;

    private static final String ORIGEM_B = """
            (SELECT imo_id AS b_imo, rin_valor_percentual AS b_pct, rin_valor_hectares AS b_ha,
                    rin_valor_absoluto AS b_abs, rin_situacao AS b_sit
               FROM RESULTADO_INDICADOR WHERE ver_id = ? AND ind_id = ?) b
            """;

    // DECODE trata NULL = NULL como igual
    private static final String DIFERENTE = """
            (DECODE(a.a_pct, b.b_pct, 1, 0) = 0 OR DECODE(a.a_ha, b.b_ha, 1, 0) = 0
              OR DECODE(a.a_abs, b.b_abs, 1, 0) = 0 OR DECODE(a.a_sit, b.b_sit, 1, 0) = 0)
            """;

    private final JdbcTemplate jdbc;

    public ComparacaoVersaoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ComparacaoVersaoResponse comparar(String sigla, Long versaoAId, Long versaoBId,
                                             boolean apenasAlterados, int pagina, int tamanho) {
        if (sigla == null || sigla.isBlank()) {
            throw new RequisicaoInvalidaException("Informe o indicador.");
        }
        if (versaoAId == null || versaoBId == null) {
            throw new RequisicaoInvalidaException("Informe as duas versões (versaoA e versaoB).");
        }
        if (versaoAId.equals(versaoBId)) {
            throw new RequisicaoInvalidaException("Escolha duas versões diferentes para comparar.");
        }
        if (pagina < 0 || tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new RequisicaoInvalidaException(
                    "Paginação inválida: pagina >= 0 e tamanho entre 1 e " + TAMANHO_MAXIMO + ".");
        }

        List<Object[]> ind = jdbc.query(
                "SELECT ind_id, ind_sigla, ind_nome, ind_unidade FROM INDICADOR WHERE UPPER(ind_sigla) = ?",
                (rs, n) -> new Object[]{rs.getLong("ind_id"),
                        new Indicador(rs.getString("ind_sigla"), rs.getString("ind_nome"), rs.getString("ind_unidade"))},
                sigla.trim().toUpperCase());
        if (ind.isEmpty()) {
            throw new RecursoNaoEncontradoException("Indicador não encontrado: " + sigla);
        }
        Long indicadorId = (Long) ind.get(0)[0];
        Indicador indicador = (Indicador) ind.get(0)[1];

        List<String> lacunas = new ArrayList<>();
        VersaoPublicada a = carregarVersao(versaoAId, indicadorId, indicador.sigla(), lacunas, "A");
        VersaoPublicada b = carregarVersao(versaoBId, indicadorId, indicador.sigla(), lacunas, "B");

        List<MotivoMudanca> motivos = ClassificadorMotivoMudanca.classificar(a, b);
        Resumo resumo = resumir(versaoAId, versaoBId, indicadorId);

        if (resumo.alterados() > 0 && motivos.isEmpty()) {
            lacunas.add("Os valores mudaram, mas regra, parâmetros e dados de origem registrados são iguais "
                    + "nas duas versões. O motivo não pôde ser identificado.");
        }

        PaginaResponse<ImovelComparado> imoveis =
                listarImoveis(versaoAId, versaoBId, indicadorId, apenasAlterados, pagina, tamanho, motivos, resumo);

        return new ComparacaoVersaoResponse(indicador, a, b, resumo, motivos, lacunas, imoveis);
    }

    // ---------------- identificacao da versao (US#19.01) ----------------

    private VersaoPublicada carregarVersao(Long versaoId, Long indicadorId, String sigla,
                                           List<String> lacunas, String lado) {
        List<VersaoPublicada> lista = jdbc.query("""
                SELECT v.ver_id, v.ver_numero, v.ver_hash, v.ver_competencia, v.ver_situacao,
                       v.ver_data_publicacao, v.con_id, c.con_nome, f.fon_id, f.fon_nome,
                       v.ver_exe_id, e.exe_dag_run_id
                  FROM VERSAO v
                  JOIN CONJUNTO_DADOS c ON c.con_id = v.con_id
                  JOIN FONTE_DO_DADO f  ON f.fon_id = c.fon_id
                  LEFT JOIN EXECUCAO_PIPELINE e ON e.exe_id = v.ver_exe_id
                 WHERE v.ver_id = ?
                """,
                (rs, n) -> cabecalho(rs),
                versaoId);
        if (lista.isEmpty()) {
            throw new RecursoNaoEncontradoException("Versão não encontrada: " + versaoId);
        }
        VersaoPublicada cab = lista.get(0);

        boolean publicada = cab.dataPublicacao() != null
                && ("VIGENTE".equals(cab.situacao()) || "ANTERIOR".equals(cab.situacao()));
        if (!publicada) {
            throw new RequisicaoInvalidaException(
                    "A versão " + versaoId + " não está publicada. Só é possível comparar versões publicadas.");
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM RESULTADO_INDICADOR WHERE ver_id = ? AND ind_id = ?",
                Long.class, versaoId, indicadorId);
        if (total == null || total == 0) {
            throw new RequisicaoInvalidaException(
                    "A versão " + versaoId + " não possui resultados do indicador " + sigla + ".");
        }

        Regra regra = carregarRegra(versaoId, indicadorId, lacunas, lado);
        List<EntradaOrigem> entradas = carregarEntradas(cab.execucaoId());
        if (entradas.isEmpty()) {
            lacunas.add("Versão " + lado + ": nenhuma entrada de dados registrada na execução; "
                    + "mudança de dado de origem não pode ser verificada.");
        }
        if (cab.execucaoId() == null) {
            lacunas.add("Versão " + lado + ": a versão não aponta a execução que a gerou.");
        }

        return new VersaoPublicada(cab.versaoId(), cab.numero(), cab.hash(), cab.competencia(), cab.situacao(),
                cab.dataPublicacao(), cab.conjuntoId(), cab.conjuntoNome(), cab.fonteId(), cab.fonteNome(),
                cab.execucaoId(), cab.dagRunId(), regra, entradas);
    }

    private VersaoPublicada cabecalho(ResultSet rs) throws SQLException {
        String hash = rs.getString("ver_hash");
        return new VersaoPublicada(
                rs.getLong("ver_id"),
                rs.getLong("ver_numero"),
                hash == null ? null : hash.trim(),
                rs.getString("ver_competencia"),
                rs.getString("ver_situacao"),
                instante(rs.getTimestamp("ver_data_publicacao")),
                rs.getLong("con_id"),
                rs.getString("con_nome"),
                rs.getLong("fon_id"),
                rs.getString("fon_nome"),
                rs.getObject("ver_exe_id", Long.class),
                rs.getString("exe_dag_run_id"),
                null,
                List.of());
    }

    private Regra carregarRegra(Long versaoId, Long indicadorId, List<String> lacunas, String lado) {
        List<Long> regras = jdbc.queryForList(
                "SELECT DISTINCT rca_id FROM RESULTADO_INDICADOR"
                        + " WHERE ver_id = ? AND ind_id = ? AND rca_id IS NOT NULL ORDER BY rca_id",
                Long.class, versaoId, indicadorId);
        if (regras.isEmpty()) {
            lacunas.add("Versão " + lado + ": os resultados não apontam regra de cálculo.");
            return null;
        }
        if (regras.size() > 1) {
            lacunas.add("Versão " + lado + ": resultados calculados com mais de uma regra; "
                    + "foi considerada a de menor id (" + regras.get(0) + ").");
        }
        Long regraId = regras.get(0);

        List<Parametro> parametros = jdbc.query("""
                SELECT prc_nome, prc_rotulo, prc_valor, prc_unidade
                  FROM PARAMETRO_REGRA_CALCULO WHERE rca_id = ? ORDER BY prc_nome
                """,
                (rs, n) -> new Parametro(rs.getString("prc_nome"), rs.getString("prc_rotulo"),
                        rs.getString("prc_valor"), rs.getString("prc_unidade")),
                regraId);

        List<Regra> regra = jdbc.query(
                "SELECT rca_id, rca_versao, rca_formula FROM REGRA_CALCULO WHERE rca_id = ?",
                (rs, n) -> new Regra(rs.getLong("rca_id"), rs.getString("rca_versao"),
                        rs.getString("rca_formula"), parametros),
                regraId);
        return regra.isEmpty() ? null : regra.get(0);
    }

    // Entradas de todas as execucoes da mesma rodada (dag run) da execucao que gerou a versao.
    private List<EntradaOrigem> carregarEntradas(Long execucaoId) {
        if (execucaoId == null) {
            return List.of();
        }
        return jdbc.query("""
                SELECT DISTINCT en.ver_id, v.ver_numero, v.ver_competencia, v.ver_hash,
                       c.con_id, c.con_nome, f.fon_nome
                  FROM ENTRADA_EXECUCAO en
                  JOIN VERSAO v          ON v.ver_id = en.ver_id
                  JOIN CONJUNTO_DADOS c  ON c.con_id = v.con_id
                  JOIN FONTE_DO_DADO f   ON f.fon_id = c.fon_id
                 WHERE en.exe_id IN (
                       SELECT e.exe_id FROM EXECUCAO_PIPELINE e
                        WHERE e.exe_id = ?
                           OR (e.exe_dag_run_id IS NOT NULL AND e.exe_dag_run_id =
                               (SELECT e2.exe_dag_run_id FROM EXECUCAO_PIPELINE e2 WHERE e2.exe_id = ?)))
                 ORDER BY c.con_nome, v.ver_numero
                """,
                (rs, n) -> {
                    String hash = rs.getString("ver_hash");
                    return new EntradaOrigem(rs.getLong("ver_id"), rs.getLong("ver_numero"),
                            rs.getString("ver_competencia"), hash == null ? null : hash.trim(),
                            rs.getLong("con_id"), rs.getString("con_nome"), rs.getString("fon_nome"));
                },
                execucaoId, execucaoId);
    }

    // ---------------- comparacao por imovel (US#19.02) ----------------

    private Resumo resumir(Long versaoAId, Long versaoBId, Long indicadorId) {
        return jdbc.query(
                "SELECT COUNT(*) AS total,"
                        + " SUM(CASE WHEN a.a_imo IS NOT NULL AND b.b_imo IS NULL THEN 1 ELSE 0 END) AS so_a,"
                        + " SUM(CASE WHEN a.a_imo IS NULL AND b.b_imo IS NOT NULL THEN 1 ELSE 0 END) AS so_b,"
                        + " SUM(CASE WHEN a.a_imo IS NOT NULL AND b.b_imo IS NOT NULL AND " + DIFERENTE
                        + "     THEN 1 ELSE 0 END) AS alterados"
                        + " FROM " + ORIGEM_A + " FULL OUTER JOIN " + ORIGEM_B + " ON a.a_imo = b.b_imo",
                rs -> {
                    rs.next();
                    long total = rs.getLong("total");
                    long soA = rs.getLong("so_a");
                    long soB = rs.getLong("so_b");
                    long alterados = rs.getLong("alterados");
                    return new Resumo(total, total - soA - soB - alterados, alterados, soA, soB);
                },
                versaoAId, indicadorId, versaoBId, indicadorId);
    }

    private PaginaResponse<ImovelComparado> listarImoveis(Long versaoAId, Long versaoBId, Long indicadorId,
                                                          boolean apenasAlterados, int pagina, int tamanho,
                                                          List<MotivoMudanca> motivos, Resumo resumo) {
        String filtro = apenasAlterados
                ? " WHERE a.a_imo IS NULL OR b.b_imo IS NULL OR " + DIFERENTE
                : "";
        long total = apenasAlterados
                ? resumo.alterados() + resumo.apenasEmA() + resumo.apenasEmB()
                : resumo.totalImoveis();

        List<ImovelComparado> itens = jdbc.query(
                "SELECT im.imo_id, im.imo_codigo_car, im.imo_nome,"
                        + " a.a_imo, a.a_pct, a.a_ha, a.a_abs, a.a_sit,"
                        + " b.b_imo, b.b_pct, b.b_ha, b.b_abs, b.b_sit"
                        + " FROM " + ORIGEM_A + " FULL OUTER JOIN " + ORIGEM_B + " ON a.a_imo = b.b_imo"
                        + " JOIN IMOVEL_RURAL im ON im.imo_id = COALESCE(a.a_imo, b.b_imo)"
                        + filtro
                        + " ORDER BY im.imo_codigo_car"
                        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, n) -> imovel(rs, motivos),
                versaoAId, indicadorId, versaoBId, indicadorId, (long) pagina * tamanho, tamanho);

        return PaginaResponse.de(itens, pagina, tamanho, total);
    }

    private ImovelComparado imovel(ResultSet rs, List<MotivoMudanca> motivos) throws SQLException {
        boolean temA = rs.getObject("a_imo") != null;
        boolean temB = rs.getObject("b_imo") != null;

        Valores va = temA ? new Valores(rs.getBigDecimal("a_pct"), rs.getBigDecimal("a_ha"),
                rs.getBigDecimal("a_abs"), rs.getString("a_sit")) : null;
        Valores vb = temB ? new Valores(rs.getBigDecimal("b_pct"), rs.getBigDecimal("b_ha"),
                rs.getBigDecimal("b_abs"), rs.getString("b_sit")) : null;

        SituacaoImovel situacao = situacao(va, vb);
        Diferenca diferenca = (va != null && vb != null)
                ? new Diferenca(subtrair(vb.percentual(), va.percentual()),
                        subtrair(vb.hectares(), va.hectares()),
                        subtrair(vb.absoluto(), va.absoluto()))
                : null;

        return new ImovelComparado(rs.getLong("imo_id"), rs.getString("imo_codigo_car"), rs.getString("imo_nome"),
                situacao, va, vb, diferenca, ClassificadorMotivoMudanca.motivosDoImovel(situacao, motivos));
    }

    static SituacaoImovel situacao(Valores a, Valores b) {
        if (a == null) {
            return SituacaoImovel.APENAS_EM_B;
        }
        if (b == null) {
            return SituacaoImovel.APENAS_EM_A;
        }
        boolean igual = iguais(a.percentual(), b.percentual())
                && iguais(a.hectares(), b.hectares())
                && iguais(a.absoluto(), b.absoluto())
                && java.util.Objects.equals(a.situacao(), b.situacao());
        return igual ? SituacaoImovel.IGUAL : SituacaoImovel.ALTERADO;
    }

    static BigDecimal subtrair(BigDecimal b, BigDecimal a) {
        return (a == null || b == null) ? null : b.subtract(a);
    }

    private static boolean iguais(BigDecimal x, BigDecimal y) {
        return x == null ? y == null : (y != null && x.compareTo(y) == 0);
    }

    private static LocalDateTime instante(java.sql.Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
