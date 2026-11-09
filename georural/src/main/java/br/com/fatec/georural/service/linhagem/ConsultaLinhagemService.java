package br.com.fatec.georural.service.linhagem;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.linhagem.ArquivoOrigemResponse;
import br.com.fatec.georural.dto.response.linhagem.EntradaLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.EtapaExecucaoResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemArquivoResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemCompletaResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemItemResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemVersaoResponse;
import br.com.fatec.georural.dto.response.linhagem.MovimentacaoZonaResponse;
import br.com.fatec.georural.dto.response.linhagem.ParametroLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.PipelineLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.RegraLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.ResultadoLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.TransformacaoLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.ValidacaoLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.VersaoLinhagemResponse;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.service.catalogo.CamadaDados;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Consulta da linhagem dos dados, do resultado ate o arquivo de origem (US#17.05).
@Service
@Transactional(readOnly = true)
public class ConsultaLinhagemService {

    private static final Map<String, String> ORDENS = Map.of(
            "calculo", "r.rin_data_calculo DESC, r.rin_id DESC",
            "imovel", "im.imo_codigo_car, i.ind_sigla, r.rin_id",
            "indicador", "i.ind_sigla, im.imo_codigo_car, r.rin_id",
            "versao", "v.ver_numero DESC, i.ind_sigla, r.rin_id"
    );

    private static final String COLUNAS_EXECUCAO = """
            SELECT e.exe_id,
                   e.exe_etapa,
                   e.exe_status,
                   e.exe_dag_run_id,
                   e.exe_mensagem,
                   u.usu_nome,
                   e.arq_bruto_id,
                   e.exe_ver_gerada_id,
                   e.exe_registros_recebidos,
                   e.exe_registros_processados,
                   e.exe_registros_aprovados,
                   e.exe_registros_rejeitados,
                   e.exe_inicio,
                   e.exe_fim,
                   e.exe_duracao_seg
              FROM EXECUCAO_PIPELINE e
              LEFT JOIN USUARIO u ON u.usu_id = e.usu_id
            """;

    private static final String COLUNAS_VERSAO = """
            SELECT v.ver_id,
                   v.ver_numero,
                   v.ver_competencia,
                   v.ver_situacao,
                   v.ver_hash,
                   v.ver_justificativa,
                   c.con_id,
                   c.con_nome,
                   t.tip_codigo,
                   v.ver_exe_id,
                   u.usu_nome,
                   v.ver_data_criacao,
                   v.ver_data_publicacao
              FROM VERSAO v
              JOIN CONJUNTO_DADOS c ON c.con_id = v.con_id
              JOIN TIPO_CONJUNTO t  ON t.tip_id = c.tip_id
              LEFT JOIN USUARIO u   ON u.usu_id = v.usu_id
            """;

    private final JdbcTemplate jdbc;

    public ConsultaLinhagemService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Resultados disponiveis para consulta de linhagem.
    public PaginaResponse<LinhagemItemResponse> listar(FiltroLinhagem filtro) {
        List<Object> parametros = new ArrayList<>();
        String where = montarWhere(filtro, parametros);

        String origem = """
                  FROM RESULTADO_INDICADOR r
                  JOIN INDICADOR i     ON i.ind_id = r.ind_id
                  JOIN IMOVEL_RURAL im ON im.imo_id = r.imo_id
                  JOIN VERSAO v        ON v.ver_id = r.ver_id
                  LEFT JOIN MUNICIPIO m ON m.mun_id = im.mun_id
                  LEFT JOIN EXECUCAO_PIPELINE e ON e.exe_id = r.exe_id
                """;

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + origem + where,
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

        List<LinhagemItemResponse> itens = jdbc.query("""
                SELECT r.rin_id,
                       i.ind_sigla,
                       r.imo_id,
                       im.imo_codigo_car,
                       im.imo_nome,
                       im.arq_bruto_id,
                       m.mun_id,
                       m.mun_nome,
                       m.mun_uf,
                       r.rin_valor_percentual,
                       r.rin_valor_hectares,
                       r.rin_situacao,
                       r.rin_vigente,
                       r.ver_id,
                       v.ver_numero,
                       v.ver_competencia,
                       r.exe_id,
                       e.exe_dag_run_id,
                       r.rin_data_calculo
                """
                + origem
                + where
                + " ORDER BY " + ORDENS.get(filtro.ordenar())
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, linha) -> new LinhagemItemResponse(
                        rs.getLong("rin_id"),
                        rs.getString("ind_sigla"),
                        rs.getLong("imo_id"),
                        rs.getString("imo_codigo_car"),
                        rs.getString("imo_nome"),
                        rs.getString("mun_nome"),
                        rs.getString("mun_uf"),
                        rs.getBigDecimal("rin_valor_percentual"),
                        rs.getBigDecimal("rin_valor_hectares"),
                        rs.getString("rin_situacao"),
                        "S".equals(rs.getString("rin_vigente")),
                        rs.getLong("ver_id"),
                        rs.getObject("ver_numero", Integer.class),
                        rs.getString("ver_competencia"),
                        rs.getObject("exe_id", Long.class),
                        rs.getString("exe_dag_run_id"),
                        rs.getObject("arq_bruto_id", Long.class) != null,
                        instante(rs.getTimestamp("rin_data_calculo"))
                ),
                parametrosPagina.toArray()
        );

        return PaginaResponse.de(
                itens, filtro.pagina(), filtro.tamanho(), totalItens);
    }

    // A cadeia inteira de um resultado.
    public LinhagemCompletaResponse doResultado(Long resultadoId) {
        if (resultadoId == null || resultadoId <= 0) {
            throw new RecursoNaoEncontradoException(
                    "Resultado nao encontrado: " + resultadoId
            );
        }

        Cabeca cabeca = jdbc.query("""
                SELECT r.rin_id,
                       i.ind_sigla,
                       i.ind_nome,
                       i.ind_unidade,
                       r.imo_id,
                       im.imo_codigo_car,
                       im.imo_nome,
                       im.imo_area_total,
                       im.arq_bruto_id,
                       m.mun_id,
                       m.mun_nome,
                       m.mun_uf,
                       r.rin_valor_percentual,
                       r.rin_valor_hectares,
                       r.rin_valor_absoluto,
                       r.rin_situacao,
                       r.rin_vigente,
                       r.rin_data_calculo,
                       r.ver_id,
                       r.exe_id,
                       r.rca_id,
                       v.ver_exe_id
                  FROM RESULTADO_INDICADOR r
                  JOIN INDICADOR i     ON i.ind_id = r.ind_id
                  JOIN IMOVEL_RURAL im ON im.imo_id = r.imo_id
                  JOIN VERSAO v        ON v.ver_id = r.ver_id
                  LEFT JOIN MUNICIPIO m ON m.mun_id = im.mun_id
                 WHERE r.rin_id = ?
                """,
                rs -> rs.next() ? new Cabeca(rs) : null,
                resultadoId
        );

        if (cabeca == null) {
            throw new RecursoNaoEncontradoException(
                    "Resultado nao encontrado: " + resultadoId
            );
        }

        List<String> lacunas = new ArrayList<>();

        // Quando o resultado nao registrou execucao, cai na que gerou a versao.
        Long execucaoId = cabeca.execucaoId() != null
                ? cabeca.execucaoId()
                : cabeca.execucaoDaVersao();

        if (cabeca.execucaoId() == null) {
            lacunas.add(cabeca.execucaoDaVersao() == null
                    ? "O resultado nao aponta execucao e a versao tambem nao, "
                            + "nao foi possivel ligar o calculo ao pipeline."
                    : "O resultado nao aponta execucao; usei a execucao que gerou "
                            + "a versao (" + cabeca.execucaoDaVersao() + ").");
        }

        EtapaExecucaoResponse calculo = execucaoId == null
                ? null
                : umaExecucao(execucaoId);

        PipelineLinhagemResponse pipeline = pipelineDe(calculo, lacunas);

        List<Long> execucaoIds = idsDePipeline(pipeline, execucaoId);
        List<EntradaLinhagemResponse> entradas = entradas(execucaoIds);
        List<TransformacaoLinhagemResponse> transformacoes = transformacoes(execucaoIds);

        Set<Long> arquivoIds = new LinkedHashSet<>();

        if (cabeca.arquivoDoImovel() != null) {
            arquivoIds.add(cabeca.arquivoDoImovel());
        } else {
            lacunas.add("O imovel do resultado nao aponta arquivo de origem.");
        }

        arquivoIds.addAll(arquivosDe(pipeline, entradas, execucaoIds));

        List<ArquivoOrigemResponse> origem = arquivos(arquivoIds);
        List<ValidacaoLinhagemResponse> qualidade = validacoes(arquivoIds);

        RegraLinhagemResponse regra = cabeca.regraId() == null
                ? null
                : regra(cabeca.regraId());

        if (regra == null) {
            lacunas.add("O resultado nao aponta regra de calculo.");
        }

        if (origem.isEmpty()) {
            lacunas.add("Nenhum arquivo de origem foi encontrado na cadeia.");
        }

        if (transformacoes.isEmpty()) {
            lacunas.add("Nenhuma transformacao registrada nas execucoes da cadeia.");
        }

        VersaoLinhagemResponse versao = umaVersao(cabeca.versaoId());

        Map<String, String> links = new LinkedHashMap<>();

        links.put("self", "/api/v1/linhagem/resultados/" + resultadoId);
        links.put("versao", "/api/v1/linhagem/versoes/" + cabeca.versaoId());

        if (!origem.isEmpty()) {
            links.put("arquivoDeOrigem",
                    "/api/v1/linhagem/arquivos/" + origem.get(0).arquivoId());
        }

        if (versao != null) {
            links.put("conjunto", "/api/v1/catalogo/" + versao.conjuntoId());
        }

        return new LinhagemCompletaResponse(
                cabeca.resultado(),
                regra,
                versao,
                calculo,
                pipeline,
                entradas,
                transformacoes,
                qualidade,
                origem,
                regra != null && calculo != null && !origem.isEmpty(),
                lacunas,
                links
        );
    }

    // A linhagem de uma versao.
    public LinhagemVersaoResponse daVersao(Long versaoId) {
        if (versaoId == null || versaoId <= 0) {
            throw new RecursoNaoEncontradoException(
                    "Versao nao encontrada: " + versaoId
            );
        }

        VersaoLinhagemResponse versao = umaVersao(versaoId);

        if (versao == null) {
            throw new RecursoNaoEncontradoException(
                    "Versao nao encontrada: " + versaoId
            );
        }

        List<String> lacunas = new ArrayList<>();

        EtapaExecucaoResponse geradora = versao.execucaoQueGerou() == null
                ? null
                : umaExecucao(versao.execucaoQueGerou());

        if (geradora == null) {
            lacunas.add("A versao nao aponta a execucao que a gerou.");
        }

        PipelineLinhagemResponse pipeline = pipelineDe(geradora, lacunas);
        List<Long> execucaoIds = idsDePipeline(pipeline, versao.execucaoQueGerou());

        List<EntradaLinhagemResponse> entradas = entradas(execucaoIds);
        List<TransformacaoLinhagemResponse> transformacoes = transformacoes(execucaoIds);

        Set<Long> arquivoIds = new LinkedHashSet<>(
                arquivosDe(pipeline, entradas, execucaoIds));

        List<ArquivoOrigemResponse> origem = arquivos(arquivoIds);

        if (origem.isEmpty()) {
            lacunas.add("Nenhum arquivo de origem foi encontrado na cadeia.");
        }

        Long resultados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM RESULTADO_INDICADOR WHERE ver_id = ?",
                Long.class,
                versaoId
        );

        Map<String, String> links = new LinkedHashMap<>();

        links.put("self", "/api/v1/linhagem/versoes/" + versaoId);
        links.put("conjunto", "/api/v1/catalogo/" + versao.conjuntoId());
        links.put("dados", "/api/v1/catalogo/" + versao.conjuntoId()
                + "/dados?versaoId=" + versaoId);
        links.put("resultados", "/api/v1/indicadores/resultados?versaoId=" + versaoId);

        return new LinhagemVersaoResponse(
                versao,
                geradora,
                pipeline,
                entradas,
                transformacoes,
                validacoes(arquivoIds),
                origem,
                resultados == null ? 0L : resultados,
                geradora != null && !origem.isEmpty(),
                lacunas,
                links
        );
    }

    // Linhagem reversa: o que saiu de um arquivo.
    public LinhagemArquivoResponse doArquivo(Long arquivoId) {
        if (arquivoId == null || arquivoId <= 0) {
            throw new RecursoNaoEncontradoException(
                    "Arquivo nao encontrado: " + arquivoId
            );
        }

        List<ArquivoOrigemResponse> achados = arquivos(Set.of(arquivoId));

        if (achados.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Arquivo nao encontrado: " + arquivoId
            );
        }

        ArquivoOrigemResponse arquivo = achados.get(0);

        Set<Long> execucaoIds = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT exe_id FROM EXECUCAO_PIPELINE WHERE arq_bruto_id = ?"
                        + " UNION"
                        + " SELECT exe_id FROM MOVIMENTACAO_ZONA"
                        + " WHERE arq_bruto_id = ? AND exe_id IS NOT NULL",
                Long.class,
                arquivoId,
                arquivoId
        ));

        List<EtapaExecucaoResponse> execucoes = execucaoIds.isEmpty()
                ? List.of()
                : execucoesPorId(execucaoIds);

        List<VersaoLinhagemResponse> versoes = versoesGeradas(execucaoIds);

        long registros = registrosCarregados(arquivo.tipoCamada(), arquivoId);

        Long derivados = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT r.rin_id)
                  FROM RESULTADO_INDICADOR r
                  JOIN IMOVEL_RURAL im ON im.imo_id = r.imo_id
                 WHERE im.arq_bruto_id = ?
                    OR r.ver_id IN (SELECT e.exe_ver_gerada_id
                                      FROM EXECUCAO_PIPELINE e
                                     WHERE e.arq_bruto_id = ?
                                       AND e.exe_ver_gerada_id IS NOT NULL)
                """,
                Long.class,
                arquivoId,
                arquivoId
        );

        List<String> indicadores = jdbc.queryForList("""
                SELECT DISTINCT i.ind_sigla
                  FROM RESULTADO_INDICADOR r
                  JOIN INDICADOR i     ON i.ind_id = r.ind_id
                  JOIN IMOVEL_RURAL im ON im.imo_id = r.imo_id
                 WHERE im.arq_bruto_id = ?
                    OR r.ver_id IN (SELECT e.exe_ver_gerada_id
                                      FROM EXECUCAO_PIPELINE e
                                     WHERE e.arq_bruto_id = ?
                                       AND e.exe_ver_gerada_id IS NOT NULL)
                 ORDER BY 1
                """,
                String.class,
                arquivoId,
                arquivoId
        );

        Map<String, String> links = new LinkedHashMap<>();

        links.put("self", "/api/v1/linhagem/arquivos/" + arquivoId);
        links.put("conjunto", "/api/v1/catalogo/" + arquivo.conjuntoId());
        links.put("relatoriosDeQualidade",
                "/api/v1/qualidade/relatorios?arquivoId=" + arquivoId);

        return new LinhagemArquivoResponse(
                arquivo,
                validacoes(Set.of(arquivoId)),
                execucoes,
                transformacoes(new ArrayList<>(execucaoIds)),
                versoes,
                registros,
                derivados == null ? 0L : derivados,
                indicadores,
                links
        );
    }

    // ----- pecas reaproveitadas -----

    private PipelineLinhagemResponse pipelineDe(
            EtapaExecucaoResponse execucao,
            List<String> lacunas) {
        if (execucao == null) {
            return new PipelineLinhagemResponse(null, List.of());
        }

        if (execucao.dagRunId() == null || execucao.dagRunId().isBlank()) {
            lacunas.add("A execucao " + execucao.id() + " nao tem dag_run_id, "
                    + "as outras etapas do pipeline nao podem ser ligadas a ela.");

            return new PipelineLinhagemResponse(null, List.of(execucao));
        }

        List<EtapaExecucaoResponse> etapas = jdbc.query(
                COLUNAS_EXECUCAO
                        + " WHERE e.exe_dag_run_id = ?"
                        + " ORDER BY e.exe_inicio, e.exe_id",
                execucaoMapper(),
                execucao.dagRunId()
        );

        return new PipelineLinhagemResponse(execucao.dagRunId(), etapas);
    }

    private List<Long> idsDePipeline(
            PipelineLinhagemResponse pipeline,
            Long execucaoId) {
        Set<Long> ids = new LinkedHashSet<>();

        if (execucaoId != null) {
            ids.add(execucaoId);
        }

        for (EtapaExecucaoResponse etapa : pipeline.etapas()) {
            ids.add(etapa.id());
        }

        return new ArrayList<>(ids);
    }

    // Arquivos alcancados pelas etapas, pelas entradas e pelas movimentacoes.
    private Set<Long> arquivosDe(
            PipelineLinhagemResponse pipeline,
            List<EntradaLinhagemResponse> entradas,
            List<Long> execucaoIds) {
        Set<Long> arquivoIds = new LinkedHashSet<>();

        for (EtapaExecucaoResponse etapa : pipeline.etapas()) {
            if (etapa.arquivoId() != null) {
                arquivoIds.add(etapa.arquivoId());
            }
        }

        for (EntradaLinhagemResponse entrada : entradas) {
            if (entrada.arquivoId() != null) {
                arquivoIds.add(entrada.arquivoId());
            }
        }

        if (!execucaoIds.isEmpty()) {
            arquivoIds.addAll(jdbc.queryForList(
                    "SELECT DISTINCT arq_bruto_id FROM MOVIMENTACAO_ZONA"
                            + " WHERE exe_id IN (" + marcas(execucaoIds.size()) + ")",
                    Long.class,
                    execucaoIds.toArray()
            ));
        }

        return arquivoIds;
    }

    private EtapaExecucaoResponse umaExecucao(Long execucaoId) {
        return jdbc.query(
                COLUNAS_EXECUCAO + " WHERE e.exe_id = ?",
                rs -> rs.next() ? execucaoMapper().mapRow(rs, 1) : null,
                execucaoId
        );
    }

    private List<EtapaExecucaoResponse> execucoesPorId(Set<Long> execucaoIds) {
        return jdbc.query(
                COLUNAS_EXECUCAO
                        + " WHERE e.exe_id IN (" + marcas(execucaoIds.size()) + ")"
                        + " ORDER BY e.exe_inicio, e.exe_id",
                execucaoMapper(),
                execucaoIds.toArray()
        );
    }

    private RowMapper<EtapaExecucaoResponse> execucaoMapper() {
        return (rs, linha) -> new EtapaExecucaoResponse(
                rs.getLong("exe_id"),
                rs.getString("exe_etapa"),
                rs.getString("exe_status"),
                rs.getString("exe_dag_run_id"),
                rs.getString("exe_mensagem"),
                rs.getString("usu_nome"),
                rs.getObject("arq_bruto_id", Long.class),
                rs.getObject("exe_ver_gerada_id", Long.class),
                rs.getObject("exe_registros_recebidos", Long.class),
                rs.getObject("exe_registros_processados", Long.class),
                rs.getObject("exe_registros_aprovados", Long.class),
                rs.getObject("exe_registros_rejeitados", Long.class),
                instante(rs.getTimestamp("exe_inicio")),
                instante(rs.getTimestamp("exe_fim")),
                rs.getObject("exe_duracao_seg", Long.class)
        );
    }

    private VersaoLinhagemResponse umaVersao(Long versaoId) {
        return jdbc.query(
                COLUNAS_VERSAO + " WHERE v.ver_id = ?",
                rs -> rs.next() ? versaoMapper().mapRow(rs, 1) : null,
                versaoId
        );
    }

    private List<VersaoLinhagemResponse> versoesGeradas(Set<Long> execucaoIds) {
        if (execucaoIds.isEmpty()) {
            return List.of();
        }

        String marcas = marcas(execucaoIds.size());
        List<Object> parametros = new ArrayList<>(execucaoIds);

        parametros.addAll(execucaoIds);

        return jdbc.query(
                COLUNAS_VERSAO
                        + " WHERE v.ver_exe_id IN (" + marcas + ")"
                        + "    OR v.ver_id IN (SELECT e2.exe_ver_gerada_id"
                        + "                      FROM EXECUCAO_PIPELINE e2"
                        + "                     WHERE e2.exe_id IN (" + marcas + ")"
                        + "                       AND e2.exe_ver_gerada_id IS NOT NULL)"
                        + " ORDER BY v.ver_numero DESC, v.ver_id",
                versaoMapper(),
                parametros.toArray()
        );
    }

    private RowMapper<VersaoLinhagemResponse> versaoMapper() {
        return (rs, linha) -> new VersaoLinhagemResponse(
                rs.getLong("ver_id"),
                rs.getObject("ver_numero", Integer.class),
                rs.getString("ver_competencia"),
                rs.getString("ver_situacao"),
                rs.getString("ver_hash"),
                rs.getString("ver_justificativa"),
                rs.getLong("con_id"),
                rs.getString("con_nome"),
                rs.getString("tip_codigo"),
                rs.getObject("ver_exe_id", Long.class),
                rs.getString("usu_nome"),
                instante(rs.getTimestamp("ver_data_criacao")),
                instante(rs.getTimestamp("ver_data_publicacao"))
        );
    }

    private List<EntradaLinhagemResponse> entradas(List<Long> execucaoIds) {
        if (execucaoIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query(
                "SELECT en.ver_id,"
                        + " v.ver_numero,"
                        + " v.ver_competencia,"
                        + " v.ver_situacao,"
                        + " en.ent_papel,"
                        + " c.con_id,"
                        + " c.con_nome,"
                        + " t.tip_codigo,"
                        + " v.ver_exe_id,"
                        + " e.arq_bruto_id"
                        + " FROM ENTRADA_EXECUCAO en"
                        + " JOIN VERSAO v         ON v.ver_id = en.ver_id"
                        + " JOIN CONJUNTO_DADOS c ON c.con_id = v.con_id"
                        + " JOIN TIPO_CONJUNTO t  ON t.tip_id = c.tip_id"
                        + " LEFT JOIN EXECUCAO_PIPELINE e ON e.exe_id = v.ver_exe_id"
                        + " WHERE en.exe_id IN (" + marcas(execucaoIds.size()) + ")"
                        + " ORDER BY c.con_nome, v.ver_numero DESC",
                (rs, linha) -> new EntradaLinhagemResponse(
                        rs.getLong("ver_id"),
                        rs.getObject("ver_numero", Integer.class),
                        rs.getString("ver_competencia"),
                        rs.getString("ver_situacao"),
                        rs.getString("ent_papel"),
                        rs.getLong("con_id"),
                        rs.getString("con_nome"),
                        rs.getString("tip_codigo"),
                        rs.getObject("ver_exe_id", Long.class),
                        rs.getObject("arq_bruto_id", Long.class)
                ),
                execucaoIds.toArray()
        );
    }

    private List<TransformacaoLinhagemResponse> transformacoes(
            List<Long> execucaoIds) {
        if (execucaoIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query(
                "SELECT tr.exe_id,"
                        + " e.exe_etapa,"
                        + " tr.tra_ordem,"
                        + " tr.tra_tipo,"
                        + " tr.tra_descricao,"
                        + " tr.tra_entrada,"
                        + " tr.tra_saida,"
                        + " tr.tra_data"
                        + " FROM TRANSFORMACAO tr"
                        + " JOIN EXECUCAO_PIPELINE e ON e.exe_id = tr.exe_id"
                        + " WHERE tr.exe_id IN (" + marcas(execucaoIds.size()) + ")"
                        + " ORDER BY tr.tra_data, tr.exe_id, tr.tra_ordem",
                (rs, linha) -> new TransformacaoLinhagemResponse(
                        rs.getLong("exe_id"),
                        rs.getString("exe_etapa"),
                        rs.getObject("tra_ordem", Integer.class),
                        rs.getString("tra_tipo"),
                        rs.getString("tra_descricao"),
                        rs.getString("tra_entrada"),
                        rs.getString("tra_saida"),
                        instante(rs.getTimestamp("tra_data"))
                ),
                execucaoIds.toArray()
        );
    }

    private List<ValidacaoLinhagemResponse> validacoes(Set<Long> arquivoIds) {
        if (arquivoIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query(
                "SELECT val.val_id,"
                        + " val.arq_bruto_id,"
                        + " a.arq_nome,"
                        + " val.exe_id,"
                        + " val.val_status_final,"
                        + " val.val_total_registros,"
                        + " val.val_registros_validos,"
                        + " val.val_registros_invalidos,"
                        + " val.val_data_execucao,"
                        + " (SELECT COUNT(*) FROM QUARENTENA q"
                        + "   WHERE q.val_id = val.val_id) AS em_quarentena"
                        + " FROM VALIDACAO val"
                        + " JOIN ARQUIVO_BRUTO a ON a.arq_bruto_id = val.arq_bruto_id"
                        + " WHERE val.arq_bruto_id IN ("
                        + marcas(arquivoIds.size()) + ")"
                        + " ORDER BY val.val_data_execucao DESC, val.val_id DESC",
                (rs, linha) -> new ValidacaoLinhagemResponse(
                        rs.getLong("val_id"),
                        rs.getLong("arq_bruto_id"),
                        rs.getString("arq_nome"),
                        rs.getObject("exe_id", Long.class),
                        rs.getString("val_status_final"),
                        rs.getObject("val_total_registros", Long.class),
                        rs.getObject("val_registros_validos", Long.class),
                        rs.getObject("val_registros_invalidos", Long.class),
                        rs.getLong("em_quarentena"),
                        instante(rs.getTimestamp("val_data_execucao"))
                ),
                arquivoIds.toArray()
        );
    }

    private List<ArquivoOrigemResponse> arquivos(Set<Long> arquivoIds) {
        if (arquivoIds.isEmpty()) {
            return List.of();
        }

        Map<Long, List<MovimentacaoZonaResponse>> zonas = zonas(arquivoIds);

        return jdbc.query(
                "SELECT a.arq_bruto_id,"
                        + " a.arq_nome,"
                        + " a.arq_formato,"
                        + " a.arq_formato_detectado,"
                        + " a.arq_competencia,"
                        + " a.arq_tamanho_bytes,"
                        + " a.arq_hash,"
                        + " a.arq_algoritmo_hash,"
                        + " a.arq_srs_declarado,"
                        + " a.arq_srs_utilizado,"
                        + " a.arq_status,"
                        + " a.arq_canal_entrada,"
                        + " a.arq_path_armazenamento,"
                        + " a.arq_caminho_interno,"
                        + " a.arq_pai_id,"
                        + " u.usu_nome,"
                        + " a.arq_data_upload,"
                        + " c.con_id,"
                        + " c.con_nome,"
                        + " c.con_forma_entrada,"
                        + " t.tip_codigo,"
                        + " f.fon_sigla,"
                        + " f.fon_nome,"
                        + " f.fon_esfera"
                        + " FROM ARQUIVO_BRUTO a"
                        + " JOIN CONJUNTO_DADOS c ON c.con_id = a.con_id"
                        + " JOIN TIPO_CONJUNTO t  ON t.tip_id = c.tip_id"
                        + " JOIN FONTE_DO_DADO f  ON f.fon_id = c.fon_id"
                        + " LEFT JOIN USUARIO u   ON u.usu_id = a.usu_id"
                        + " WHERE a.arq_bruto_id IN ("
                        + marcas(arquivoIds.size()) + ")"
                        + " ORDER BY a.arq_data_upload, a.arq_bruto_id",
                (rs, linha) -> {
                    long id = rs.getLong("arq_bruto_id");

                    return new ArquivoOrigemResponse(
                            id,
                            rs.getString("arq_nome"),
                            rs.getString("arq_formato"),
                            rs.getString("arq_formato_detectado"),
                            rs.getString("arq_competencia"),
                            rs.getObject("arq_tamanho_bytes", Long.class),
                            rs.getString("arq_hash"),
                            rs.getString("arq_algoritmo_hash"),
                            rs.getObject("arq_srs_declarado", Integer.class),
                            rs.getObject("arq_srs_utilizado", Integer.class),
                            rs.getString("arq_status"),
                            rs.getString("arq_canal_entrada"),
                            rs.getString("arq_path_armazenamento"),
                            rs.getString("arq_caminho_interno"),
                            rs.getObject("arq_pai_id", Long.class),
                            rs.getString("usu_nome"),
                            instante(rs.getTimestamp("arq_data_upload")),
                            rs.getLong("con_id"),
                            rs.getString("con_nome"),
                            rs.getString("tip_codigo"),
                            rs.getString("fon_sigla"),
                            rs.getString("fon_nome"),
                            rs.getString("fon_esfera"),
                            rs.getString("con_forma_entrada"),
                            zonas.getOrDefault(id, List.of())
                    );
                },
                arquivoIds.toArray()
        );
    }

    private Map<Long, List<MovimentacaoZonaResponse>> zonas(Set<Long> arquivoIds) {
        Map<Long, List<MovimentacaoZonaResponse>> porArquivo = new LinkedHashMap<>();

        jdbc.query(
                "SELECT arq_bruto_id, mov_zona, mov_status, mov_obs, exe_id, mov_data"
                        + " FROM MOVIMENTACAO_ZONA"
                        + " WHERE arq_bruto_id IN (" + marcas(arquivoIds.size()) + ")"
                        + " ORDER BY arq_bruto_id, mov_data, mov_id",
                rs -> {
                    porArquivo.computeIfAbsent(
                                    rs.getLong("arq_bruto_id"),
                                    chave -> new ArrayList<>())
                            .add(new MovimentacaoZonaResponse(
                                    rs.getString("mov_zona"),
                                    rs.getString("mov_status"),
                                    rs.getString("mov_obs"),
                                    rs.getObject("exe_id", Long.class),
                                    instante(rs.getTimestamp("mov_data"))
                            ));
                },
                arquivoIds.toArray()
        );

        return porArquivo;
    }

    private RegraLinhagemResponse regra(Long regraId) {
        List<ParametroLinhagemResponse> parametros = jdbc.query("""
                SELECT prc_nome, prc_rotulo, prc_valor, prc_unidade
                  FROM PARAMETRO_REGRA_CALCULO
                 WHERE rca_id = ?
                 ORDER BY prc_nome
                """,
                (rs, linha) -> new ParametroLinhagemResponse(
                        rs.getString("prc_nome"),
                        rs.getString("prc_rotulo"),
                        rs.getString("prc_valor"),
                        rs.getString("prc_unidade")
                ),
                regraId
        );

        return jdbc.query("""
                SELECT rca_id, rca_versao, rca_formula, rca_descricao,
                       rca_autor, rca_motivo, rca_vigente, rca_data_criacao
                  FROM REGRA_CALCULO
                 WHERE rca_id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }

                    return new RegraLinhagemResponse(
                            rs.getLong("rca_id"),
                            rs.getString("rca_versao"),
                            rs.getString("rca_formula"),
                            rs.getString("rca_descricao"),
                            rs.getString("rca_autor"),
                            rs.getString("rca_motivo"),
                            "S".equals(rs.getString("rca_vigente")),
                            instante(rs.getTimestamp("rca_data_criacao")),
                            parametros
                    );
                },
                regraId
        );
    }

    // Quantos registros o arquivo carregou na tabela da camada dele.
    private long registrosCarregados(String tipoCamada, Long arquivoId) {
        Optional<CamadaDados> camada = CamadaDados.porTipoConjunto(tipoCamada);

        if (camada.isEmpty()) {
            return 0L;
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + camada.get().tabela()
                        + " WHERE arq_bruto_id = ?",
                Long.class,
                arquivoId
        );

        return total == null ? 0L : total;
    }

    private String montarWhere(
            FiltroLinhagem filtro,
            List<Object> parametros) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        if (filtro.indicador() != null) {
            where.append(" AND UPPER(i.ind_sigla) = ?");
            parametros.add(filtro.indicador());
        }

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

        if (filtro.apenasPublicadas()) {
            where.append(" AND v.ver_data_publicacao IS NOT NULL");
        }

        if (filtro.texto() != null) {
            where.append(" AND (UPPER(im.imo_codigo_car) LIKE ?"
                    + " OR UPPER(NVL(im.imo_nome, ' ')) LIKE ?"
                    + " OR UPPER(NVL(m.mun_nome, ' ')) LIKE ?)");

            String curinga = "%" + filtro.texto() + "%";

            parametros.add(curinga);
            parametros.add(curinga);
            parametros.add(curinga);
        }

        return where.toString();
    }

    private String marcas(int quantidade) {
        return String.join(", ", Collections.nCopies(quantidade, "?"));
    }

    private LocalDateTime instante(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    // Cabeca da consulta de resultado, lida uma vez e reusada na montagem.
    private record Cabeca(
            ResultadoLinhagemResponse resultado,
            Long versaoId,
            Long execucaoId,
            Long execucaoDaVersao,
            Long regraId,
            Long arquivoDoImovel
    ) {
        Cabeca(ResultSet rs) throws SQLException {
            this(
                    new ResultadoLinhagemResponse(
                            rs.getLong("rin_id"),
                            rs.getString("ind_sigla"),
                            rs.getString("ind_nome"),
                            rs.getString("ind_unidade"),
                            rs.getLong("imo_id"),
                            rs.getString("imo_codigo_car"),
                            rs.getString("imo_nome"),
                            rs.getBigDecimal("imo_area_total"),
                            rs.getObject("mun_id", Long.class),
                            rs.getString("mun_nome"),
                            rs.getString("mun_uf"),
                            rs.getBigDecimal("rin_valor_percentual"),
                            rs.getBigDecimal("rin_valor_hectares"),
                            rs.getBigDecimal("rin_valor_absoluto"),
                            rs.getString("rin_situacao"),
                            "S".equals(rs.getString("rin_vigente")),
                            rs.getTimestamp("rin_data_calculo") == null
                                    ? null
                                    : rs.getTimestamp("rin_data_calculo").toLocalDateTime()
                    ),
                    rs.getLong("ver_id"),
                    rs.getObject("exe_id", Long.class),
                    rs.getObject("ver_exe_id", Long.class),
                    rs.getObject("rca_id", Long.class),
                    rs.getObject("arq_bruto_id", Long.class)
            );
        }
    }
}
