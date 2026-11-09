package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.dto.response.ContagemResponse;
import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.linhagem.EntradaLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.EtapaExecucaoResponse;
import br.com.fatec.georural.dto.response.linhagem.MovimentacaoZonaResponse;
import br.com.fatec.georural.dto.response.linhagem.TransformacaoLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.ValidacaoLinhagemResponse;
import br.com.fatec.georural.dto.response.pipeline.ExecucaoDetalheResponse;
import br.com.fatec.georural.dto.response.pipeline.ExecucaoEmAndamentoResponse;
import br.com.fatec.georural.dto.response.pipeline.ExecucaoItemResponse;
import br.com.fatec.georural.dto.response.pipeline.FalhaRecenteResponse;
import br.com.fatec.georural.dto.response.pipeline.RodadaPipelineResponse;
import br.com.fatec.georural.dto.response.pipeline.SituacaoPipelineResponse;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Historico e situacao das execucoes das pipelines (US#17.06).
@Service
@Transactional(readOnly = true)
public class HistoricoExecucaoService {

    private static final int LIMITE_EM_ANDAMENTO = 50;
    private static final int LIMITE_FALHAS = 10;
    private static final int LIMITE_SEGUNDOS_PADRAO = 3600;

    // Mesma regra do ExecucaoConsultaService: rodada do portal ou com usuario e manual.
    private static final String EXPRESSAO_AUTOMATICA = """
            CASE WHEN NVL(e.exe_dag_run_id, 'x') LIKE 'portal-%' THEN 0
                 WHEN e.usu_id IS NOT NULL THEN 0
                 WHEN EXISTS (SELECT 1
                                FROM EXECUCAO_PIPELINE ea
                               WHERE ea.exe_dag_run_id = e.exe_dag_run_id
                                 AND ea.usu_id IS NOT NULL) THEN 0
                 ELSE 1 END
            """;

    private static final String ORIGEM = """
              FROM EXECUCAO_PIPELINE e
              JOIN CONJUNTO_DADOS c ON c.con_id = e.con_id
              JOIN TIPO_CONJUNTO t  ON t.tip_id = c.tip_id
              JOIN FONTE_DO_DADO f  ON f.fon_id = c.fon_id
              LEFT JOIN ARQUIVO_BRUTO a ON a.arq_bruto_id = e.arq_bruto_id
              LEFT JOIN USUARIO u       ON u.usu_id = e.usu_id
            """;

    private static final String COLUNAS = """
            SELECT e.exe_id,
                   e.exe_etapa,
                   e.exe_status,
                   e.exe_dag_run_id,
                   c.con_id,
                   c.con_nome,
                   t.tip_codigo,
                   f.fon_sigla,
                   e.arq_bruto_id,
                   a.arq_nome,
                   a.arq_competencia,
                   u.usu_nome,
                   e.exe_mensagem,
                   e.exe_ver_gerada_id,
                   e.exe_registros_recebidos,
                   e.exe_registros_processados,
                   e.exe_registros_aprovados,
                   e.exe_registros_rejeitados,
                   e.exe_inicio,
                   e.exe_fim,
                   e.exe_duracao_seg,
            """;

    private static final Map<String, String> ORDENS = Map.of(
            "inicio", "e.exe_inicio DESC, e.exe_id DESC",
            "duracao", "NVL(e.exe_duracao_seg, 0) DESC, e.exe_id DESC",
            "etapa", "e.exe_etapa, e.exe_inicio DESC, e.exe_id",
            "conjunto", "c.con_nome, e.exe_inicio DESC, e.exe_id"
    );

    private static final Map<String, String> ORDENS_RODADA = Map.of(
            "inicio", "MIN(e.exe_inicio) DESC",
            "duracao", "NVL(SUM(e.exe_duracao_seg), 0) DESC",
            "etapa", "MIN(e.exe_etapa), MIN(e.exe_inicio) DESC",
            "conjunto", "MIN(c.con_nome), MIN(e.exe_inicio) DESC"
    );

    private final JdbcTemplate jdbc;

    public HistoricoExecucaoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Pagina de execucoes, uma linha por etapa.
    public PaginaResponse<ExecucaoItemResponse> execucoes(FiltroExecucao filtro) {
        List<Object> parametros = new ArrayList<>();
        String where = montarWhere(filtro, parametros, false);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + ORIGEM + where,
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

        List<ExecucaoItemResponse> itens = jdbc.query(
                COLUNAS + "       (" + EXPRESSAO_AUTOMATICA + ") AS automatica"
                        + ORIGEM
                        + where
                        + " ORDER BY " + ORDENS.get(filtro.ordenar())
                        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                itemMapper(),
                parametrosPagina.toArray()
        );

        return PaginaResponse.de(
                itens, filtro.pagina(), filtro.tamanho(), totalItens);
    }

    // Detalhe de uma execucao, com o que ela consumiu, fez e moveu.
    public ExecucaoDetalheResponse detalhar(Long execucaoId) {
        if (execucaoId == null || execucaoId <= 0) {
            throw new RecursoNaoEncontradoException(
                    "Execucao nao encontrada: " + execucaoId
            );
        }

        List<ExecucaoItemResponse> achados = jdbc.query(
                COLUNAS + "       (" + EXPRESSAO_AUTOMATICA + ") AS automatica"
                        + ORIGEM
                        + " WHERE e.exe_id = ?",
                itemMapper(),
                execucaoId
        );

        if (achados.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Execucao nao encontrada: " + execucaoId
            );
        }

        ExecucaoItemResponse execucao = achados.get(0);

        List<EtapaExecucaoResponse> irmas = execucao.dagRunId() == null
                ? List.of()
                : etapasDaRodada(List.of(execucao.dagRunId()));

        Map<String, String> links = new LinkedHashMap<>();

        links.put("self", "/api/v1/execucoes/" + execucaoId);
        links.put("conjunto", "/api/v1/catalogo/" + execucao.conjuntoId());

        if (execucao.dagRunId() != null) {
            links.put("rodada", "/api/v1/execucoes/pipelines/" + execucao.dagRunId());
        }

        if (execucao.arquivoId() != null) {
            links.put("linhagemDoArquivo",
                    "/api/v1/linhagem/arquivos/" + execucao.arquivoId());
            links.put("relatoriosDeQualidade",
                    "/api/v1/qualidade/relatorios?arquivoId=" + execucao.arquivoId());
        }

        return new ExecucaoDetalheResponse(
                execucao,
                irmas,
                entradas(execucaoId),
                transformacoes(List.of(execucaoId)),
                movimentacoes(execucaoId),
                validacoes(execucaoId),
                links
        );
    }

    // Historico por rodada: uma linha por dag_run_id, com a linha do tempo das etapas.
    public PaginaResponse<RodadaPipelineResponse> rodadas(FiltroExecucao filtro) {
        List<Object> parametros = new ArrayList<>();
        String where = montarWhere(filtro, parametros, true);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT e.exe_dag_run_id"
                        + ORIGEM
                        + where
                        + " GROUP BY e.exe_dag_run_id, e.con_id)",
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

        record Cabeca(
                String dagRunId,
                Long conjuntoId,
                String conjunto,
                String tipoCamada,
                String fonteSigla,
                Long arquivoId,
                String arquivo,
                String competencia,
                boolean automatica
        ) {}

        List<Cabeca> cabecas = jdbc.query(
                "SELECT e.exe_dag_run_id,"
                        + " e.con_id,"
                        + " MIN(c.con_nome) AS con_nome,"
                        + " MIN(t.tip_codigo) AS tip_codigo,"
                        + " MIN(f.fon_sigla) AS fon_sigla,"
                        + " MIN(e.arq_bruto_id) AS arq_bruto_id,"
                        + " MIN(a.arq_nome) AS arq_nome,"
                        + " MIN(a.arq_competencia) AS arq_competencia,"
                        + " MIN(CASE WHEN e.usu_id IS NOT NULL THEN 0 ELSE 1 END) AS sem_usuario"
                        + ORIGEM
                        + where
                        + " GROUP BY e.exe_dag_run_id, e.con_id"
                        + " ORDER BY " + ORDENS_RODADA.get(filtro.ordenar())
                        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, linha) -> {
                    String dagRunId = rs.getString("exe_dag_run_id");

                    boolean doPortal = dagRunId != null
                            && dagRunId.startsWith("portal-");

                    return new Cabeca(
                            dagRunId,
                            rs.getObject("con_id", Long.class),
                            rs.getString("con_nome"),
                            rs.getString("tip_codigo"),
                            rs.getString("fon_sigla"),
                            rs.getObject("arq_bruto_id", Long.class),
                            rs.getString("arq_nome"),
                            rs.getString("arq_competencia"),
                            !doPortal && rs.getInt("sem_usuario") == 1
                    );
                },
                parametrosPagina.toArray()
        );

        List<String> dagRunIds = cabecas.stream()
                .map(Cabeca::dagRunId)
                .toList();

        Map<String, List<EtapaExecucaoResponse>> porRodada =
                agruparEtapas(etapasDaRodada(dagRunIds));

        List<RodadaPipelineResponse> itens = new ArrayList<>();

        for (Cabeca cabeca : cabecas) {
            itens.add(montarRodada(
                    cabeca.dagRunId(),
                    cabeca.conjuntoId(),
                    cabeca.conjunto(),
                    cabeca.tipoCamada(),
                    cabeca.fonteSigla(),
                    cabeca.arquivoId(),
                    cabeca.arquivo(),
                    cabeca.competencia(),
                    cabeca.automatica(),
                    porRodada.getOrDefault(cabeca.dagRunId(), List.of())
            ));
        }

        return PaginaResponse.de(
                itens, filtro.pagina(), filtro.tamanho(), totalItens);
    }

    // Uma rodada inteira.
    public RodadaPipelineResponse rodada(String dagRunId) {
        if (dagRunId == null || dagRunId.isBlank()) {
            throw new RequisicaoInvalidaException(
                    "Informe o dagRunId da rodada."
            );
        }

        List<ExecucaoItemResponse> execucoes = jdbc.query(
                COLUNAS + "       (" + EXPRESSAO_AUTOMATICA + ") AS automatica"
                        + ORIGEM
                        + " WHERE e.exe_dag_run_id = ?"
                        + " ORDER BY e.exe_inicio, e.exe_id",
                itemMapper(),
                dagRunId.trim()
        );

        if (execucoes.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Rodada nao encontrada: " + dagRunId
            );
        }

        ExecucaoItemResponse primeira = execucoes.get(0);

        return montarRodada(
                dagRunId.trim(),
                primeira.conjuntoId(),
                primeira.conjunto(),
                primeira.tipoCamada(),
                primeira.fonteSigla(),
                primeira.arquivoId(),
                primeira.arquivo(),
                primeira.competencia(),
                primeira.automatica(),
                etapasDaRodada(List.of(dagRunId.trim()))
        );
    }

    // Como o pipeline esta agora.
    public SituacaoPipelineResponse situacao(
            Long conjuntoId,
            Integer dias,
            Integer limiteSegundos) {
        int janela = dias == null ? 7 : dias;

        if (janela < 1 || janela > 365) {
            throw new RequisicaoInvalidaException(
                    "A janela em dias deve estar entre 1 e 365."
            );
        }

        int limite = limiteSegundos == null
                ? LIMITE_SEGUNDOS_PADRAO
                : limiteSegundos;

        if (limite < 1) {
            throw new RequisicaoInvalidaException(
                    "O limite em segundos deve ser maior que zero."
            );
        }

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime desde = agora.minusDays(janela);
        Timestamp inicioJanela = Timestamp.valueOf(desde);

        String filtroConjunto = conjuntoId == null
                ? ""
                : " AND e.con_id = ?";

        List<Object> base = new ArrayList<>();

        base.add(inicioJanela);

        if (conjuntoId != null) {
            base.add(conjuntoId);
        }

        List<ExecucaoEmAndamentoResponse> emAndamento = jdbc.query(
                "SELECT e.exe_id, e.exe_etapa, e.exe_dag_run_id, e.con_id,"
                        + " c.con_nome, a.arq_nome, u.usu_nome, e.exe_inicio"
                        + ORIGEM
                        + " WHERE e.exe_status = 'EM_ANDAMENTO'"
                        + (conjuntoId == null ? "" : " AND e.con_id = ?")
                        + " ORDER BY e.exe_inicio"
                        + " FETCH FIRST " + LIMITE_EM_ANDAMENTO + " ROWS ONLY",
                (rs, linha) -> {
                    LocalDateTime inicio = instante(rs.getTimestamp("exe_inicio"));

                    long segundos = inicio == null
                            ? 0L
                            : Math.max(0L, Duration.between(inicio, agora).getSeconds());

                    return new ExecucaoEmAndamentoResponse(
                            rs.getLong("exe_id"),
                            rs.getString("exe_etapa"),
                            rs.getString("exe_dag_run_id"),
                            rs.getObject("con_id", Long.class),
                            rs.getString("con_nome"),
                            rs.getString("arq_nome"),
                            rs.getString("usu_nome"),
                            inicio,
                            segundos,
                            segundos > limite
                    );
                },
                conjuntoId == null
                        ? new Object[0]
                        : new Object[]{conjuntoId}
        );

        List<ContagemResponse> porStatus = contagem(
                "e.exe_status", inicioJanela, conjuntoId, filtroConjunto);

        List<ContagemResponse> porEtapa = contagem(
                "e.exe_etapa", inicioJanela, conjuntoId, filtroConjunto);

        List<FalhaRecenteResponse> falhas = jdbc.query(
                "SELECT e.exe_id, e.exe_etapa, e.exe_dag_run_id, e.con_id,"
                        + " c.con_nome, a.arq_nome, e.exe_mensagem, e.exe_inicio"
                        + ORIGEM
                        + " WHERE e.exe_status = 'FALHOU'"
                        + "   AND e.exe_inicio >= ?"
                        + filtroConjunto
                        + " ORDER BY e.exe_inicio DESC, e.exe_id DESC"
                        + " FETCH FIRST " + LIMITE_FALHAS + " ROWS ONLY",
                (rs, linha) -> new FalhaRecenteResponse(
                        rs.getLong("exe_id"),
                        rs.getString("exe_etapa"),
                        rs.getString("exe_dag_run_id"),
                        rs.getObject("con_id", Long.class),
                        rs.getString("con_nome"),
                        rs.getString("arq_nome"),
                        rs.getString("exe_mensagem"),
                        instante(rs.getTimestamp("exe_inicio"))
                ),
                base.toArray()
        );

        Long[] rodadas = rodadasNaJanela(inicioJanela, conjuntoId, filtroConjunto);

        Timestamp ultima = jdbc.queryForObject(
                "SELECT MAX(e.exe_fim)"
                        + ORIGEM
                        + " WHERE e.exe_status = 'CONCLUIDA'"
                        + filtroConjunto,
                Timestamp.class,
                conjuntoId == null
                        ? new Object[0]
                        : new Object[]{conjuntoId}
        );

        return new SituacaoPipelineResponse(
                agora,
                janela,
                desde,
                limite,
                emAndamento,
                porStatus,
                porEtapa,
                falhas,
                rodadas[0],
                rodadas[1],
                rodadas[2],
                instante(ultima)
        );
    }

    // ----- pecas -----

    // Rodadas da janela, no total, automaticas e manuais.
    private Long[] rodadasNaJanela(
            Timestamp inicioJanela,
            Long conjuntoId,
            String filtroConjunto) {
        List<Object> parametros = new ArrayList<>();

        parametros.add(inicioJanela);

        if (conjuntoId != null) {
            parametros.add(conjuntoId);
        }

        return jdbc.query(
                "SELECT COUNT(*) AS total,"
                        + " NVL(SUM(automatica), 0) AS automaticas"
                        + " FROM (SELECT MIN(CASE WHEN e.usu_id IS NOT NULL"
                        + "                       THEN 0 ELSE 1 END)"
                        + "              * CASE WHEN e.exe_dag_run_id LIKE 'portal-%'"
                        + "                     THEN 0 ELSE 1 END AS automatica"
                        + ORIGEM
                        + " WHERE e.exe_dag_run_id IS NOT NULL"
                        + "   AND e.exe_inicio >= ?"
                        + filtroConjunto
                        + " GROUP BY e.exe_dag_run_id, e.con_id)",
                rs -> {
                    if (!rs.next()) {
                        return new Long[]{0L, 0L, 0L};
                    }

                    long total = rs.getLong("total");
                    long automaticas = rs.getLong("automaticas");

                    return new Long[]{total, automaticas, total - automaticas};
                },
                parametros.toArray()
        );
    }

    private List<ContagemResponse> contagem(
            String coluna,
            Timestamp inicioJanela,
            Long conjuntoId,
            String filtroConjunto) {
        List<Object> parametros = new ArrayList<>();

        parametros.add(inicioJanela);

        if (conjuntoId != null) {
            parametros.add(conjuntoId);
        }

        return jdbc.query(
                "SELECT " + coluna + " AS rotulo, COUNT(*) AS quantidade"
                        + ORIGEM
                        + " WHERE e.exe_inicio >= ?"
                        + filtroConjunto
                        + " GROUP BY " + coluna
                        + " ORDER BY COUNT(*) DESC, 1",
                (rs, linha) -> new ContagemResponse(
                        rs.getString("rotulo"),
                        rs.getLong("quantidade")
                ),
                parametros.toArray()
        );
    }

    // Monta a rodada a partir das etapas, sem inventar numero agregado.
    private RodadaPipelineResponse montarRodada(
            String dagRunId,
            Long conjuntoId,
            String conjunto,
            String tipoCamada,
            String fonteSigla,
            Long arquivoId,
            String arquivo,
            String competencia,
            boolean automatica,
            List<EtapaExecucaoResponse> etapas) {
        String situacao = "CONCLUIDA";
        String etapaComFalha = null;
        String mensagem = null;
        int concluidas = 0;
        int falhadas = 0;
        long rejeitados = 0;
        boolean temRejeitados = false;
        Long recebidos = null;
        Long processados = null;
        Long aprovados = null;
        LocalDateTime inicio = null;
        LocalDateTime fim = null;
        long duracao = 0;
        boolean emAndamento = false;
        boolean comRessalva = false;

        for (EtapaExecucaoResponse etapa : etapas) {
            if ("FALHOU".equals(etapa.status())) {
                falhadas++;

                if (etapaComFalha == null) {
                    etapaComFalha = etapa.etapa();
                    mensagem = etapa.mensagem();
                }
            } else if ("CONCLUIDA".equals(etapa.status())) {
                concluidas++;
            } else if ("EM_ANDAMENTO".equals(etapa.status())) {
                emAndamento = true;
            } else if ("COM_RESSALVA".equals(etapa.status())) {
                comRessalva = true;
                concluidas++;
            }

            if (etapa.registrosRejeitados() != null) {
                rejeitados += etapa.registrosRejeitados();
                temRejeitados = true;
            }

            // Recebidos e o que a primeira etapa informou.
            if (recebidos == null && etapa.registrosRecebidos() != null) {
                recebidos = etapa.registrosRecebidos();
            }

            // Processados e aprovados ficam com a ultima etapa que informou.
            if (etapa.registrosProcessados() != null) {
                processados = etapa.registrosProcessados();
            }

            if (etapa.registrosAprovados() != null) {
                aprovados = etapa.registrosAprovados();
            }

            if (etapa.inicio() != null
                    && (inicio == null || etapa.inicio().isBefore(inicio))) {
                inicio = etapa.inicio();
            }

            if (etapa.fim() != null
                    && (fim == null || etapa.fim().isAfter(fim))) {
                fim = etapa.fim();
            }

            if (etapa.duracaoSegundos() != null) {
                duracao += etapa.duracaoSegundos();
            }
        }

        if (falhadas > 0) {
            situacao = "FALHOU";
        } else if (emAndamento) {
            situacao = "EM_ANDAMENTO";
        } else if (comRessalva) {
            situacao = "COM_RESSALVA";
        } else if (etapas.isEmpty()) {
            situacao = "SEM_ETAPA";
        }

        Long versaoGerada = etapas.stream()
                .map(EtapaExecucaoResponse::versaoGeradaId)
                .filter(id -> id != null)
                .reduce((primeira, ultima) -> ultima)
                .orElse(null);

        return new RodadaPipelineResponse(
                dagRunId,
                conjuntoId,
                conjunto,
                tipoCamada,
                fonteSigla,
                arquivoId,
                arquivo,
                competencia,
                automatica,
                situacao,
                etapaComFalha,
                mensagem,
                etapas.size(),
                concluidas,
                falhadas,
                recebidos,
                processados,
                aprovados,
                temRejeitados ? rejeitados : null,
                versaoGerada,
                inicio,
                fim,
                etapas.isEmpty() ? null : duracao,
                etapas
        );
    }

    private List<EtapaExecucaoResponse> etapasDaRodada(List<String> dagRunIds) {
        if (dagRunIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query("""
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
                """
                + " WHERE e.exe_dag_run_id IN (" + marcas(dagRunIds.size()) + ")"
                + " ORDER BY e.exe_inicio, e.exe_id",
                (rs, linha) -> new EtapaExecucaoResponse(
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
                ),
                dagRunIds.toArray()
        );
    }

    private Map<String, List<EtapaExecucaoResponse>> agruparEtapas(
            List<EtapaExecucaoResponse> etapas) {
        Map<String, List<EtapaExecucaoResponse>> porRodada = new LinkedHashMap<>();

        for (EtapaExecucaoResponse etapa : etapas) {
            porRodada.computeIfAbsent(etapa.dagRunId(), chave -> new ArrayList<>())
                    .add(etapa);
        }

        return porRodada;
    }

    private List<EntradaLinhagemResponse> entradas(Long execucaoId) {
        return jdbc.query("""
                SELECT en.ver_id, v.ver_numero, v.ver_competencia, v.ver_situacao,
                       en.ent_papel, c.con_id, c.con_nome, t.tip_codigo,
                       v.ver_exe_id, e.arq_bruto_id
                  FROM ENTRADA_EXECUCAO en
                  JOIN VERSAO v         ON v.ver_id = en.ver_id
                  JOIN CONJUNTO_DADOS c ON c.con_id = v.con_id
                  JOIN TIPO_CONJUNTO t  ON t.tip_id = c.tip_id
                  LEFT JOIN EXECUCAO_PIPELINE e ON e.exe_id = v.ver_exe_id
                 WHERE en.exe_id = ?
                 ORDER BY c.con_nome, v.ver_numero DESC
                """,
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
                execucaoId
        );
    }

    private List<TransformacaoLinhagemResponse> transformacoes(
            List<Long> execucaoIds) {
        if (execucaoIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query(
                "SELECT tr.exe_id, e.exe_etapa, tr.tra_ordem, tr.tra_tipo,"
                        + " tr.tra_descricao, tr.tra_entrada, tr.tra_saida, tr.tra_data"
                        + " FROM TRANSFORMACAO tr"
                        + " JOIN EXECUCAO_PIPELINE e ON e.exe_id = tr.exe_id"
                        + " WHERE tr.exe_id IN (" + marcas(execucaoIds.size()) + ")"
                        + " ORDER BY tr.tra_ordem, tr.tra_id",
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

    private List<MovimentacaoZonaResponse> movimentacoes(Long execucaoId) {
        return jdbc.query("""
                SELECT mov_zona, mov_status, mov_obs, exe_id, mov_data
                  FROM MOVIMENTACAO_ZONA
                 WHERE exe_id = ?
                 ORDER BY mov_data, mov_id
                """,
                (rs, linha) -> new MovimentacaoZonaResponse(
                        rs.getString("mov_zona"),
                        rs.getString("mov_status"),
                        rs.getString("mov_obs"),
                        rs.getObject("exe_id", Long.class),
                        instante(rs.getTimestamp("mov_data"))
                ),
                execucaoId
        );
    }

    private List<ValidacaoLinhagemResponse> validacoes(Long execucaoId) {
        return jdbc.query("""
                SELECT val.val_id, val.arq_bruto_id, a.arq_nome, val.exe_id,
                       val.val_status_final, val.val_total_registros,
                       val.val_registros_validos, val.val_registros_invalidos,
                       val.val_data_execucao,
                       (SELECT COUNT(*) FROM QUARENTENA q
                         WHERE q.val_id = val.val_id) AS em_quarentena
                  FROM VALIDACAO val
                  JOIN ARQUIVO_BRUTO a ON a.arq_bruto_id = val.arq_bruto_id
                 WHERE val.exe_id = ?
                 ORDER BY val.val_data_execucao DESC, val.val_id DESC
                """,
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
                execucaoId
        );
    }

    private RowMapper<ExecucaoItemResponse> itemMapper() {
        return (rs, linha) -> new ExecucaoItemResponse(
                rs.getLong("exe_id"),
                rs.getString("exe_etapa"),
                rs.getString("exe_status"),
                rs.getString("exe_dag_run_id"),
                rs.getInt("automatica") == 1,
                rs.getObject("con_id", Long.class),
                rs.getString("con_nome"),
                rs.getString("tip_codigo"),
                rs.getString("fon_sigla"),
                rs.getObject("arq_bruto_id", Long.class),
                rs.getString("arq_nome"),
                rs.getString("arq_competencia"),
                rs.getString("usu_nome"),
                rs.getString("exe_mensagem"),
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

    private String montarWhere(
            FiltroExecucao filtro,
            List<Object> parametros,
            boolean exigeDagRun) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        if (exigeDagRun) {
            where.append(" AND e.exe_dag_run_id IS NOT NULL");
        }

        if (filtro.conjuntoId() != null) {
            where.append(" AND e.con_id = ?");
            parametros.add(filtro.conjuntoId());
        }

        if (filtro.etapa() != null) {
            where.append(" AND e.exe_etapa = ?");
            parametros.add(filtro.etapa());
        }

        if (filtro.status() != null) {
            where.append(" AND e.exe_status = ?");
            parametros.add(filtro.status());
        }

        if (filtro.dagRunId() != null) {
            where.append(" AND e.exe_dag_run_id = ?");
            parametros.add(filtro.dagRunId());
        }

        if (filtro.arquivoId() != null) {
            where.append(" AND e.arq_bruto_id = ?");
            parametros.add(filtro.arquivoId());
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

        if (filtro.automatica() != null) {
            where.append(" AND (").append(EXPRESSAO_AUTOMATICA).append(") = ?");
            parametros.add(filtro.automatica() ? 1 : 0);
        }

        // Rodada com falha: existe etapa FALHOU no mesmo dag run.
        if (filtro.comFalha() != null) {
            where.append(filtro.comFalha() ? " AND EXISTS" : " AND NOT EXISTS")
                    .append(" (SELECT 1 FROM EXECUCAO_PIPELINE ef"
                            + " WHERE ef.exe_status = 'FALHOU'"
                            + " AND (ef.exe_dag_run_id = e.exe_dag_run_id"
                            + "   OR ef.exe_id = e.exe_id))");
        }

        if (filtro.desde() != null) {
            where.append(" AND e.exe_inicio >= ?");
            parametros.add(Timestamp.valueOf(filtro.desde().atStartOfDay()));
        }

        // O dia final entra inteiro.
        if (filtro.ate() != null) {
            where.append(" AND e.exe_inicio < ?");
            parametros.add(Timestamp.valueOf(
                    filtro.ate().plusDays(1).atStartOfDay()));
        }

        if (filtro.texto() != null) {
            where.append(" AND (UPPER(c.con_nome) LIKE ?"
                    + " OR UPPER(NVL(a.arq_nome, ' ')) LIKE ?"
                    + " OR UPPER(NVL(e.exe_dag_run_id, ' ')) LIKE ?"
                    + " OR UPPER(NVL(e.exe_mensagem, ' ')) LIKE ?)");

            String curinga = "%" + filtro.texto() + "%";

            for (int i = 0; i < 4; i++) {
                parametros.add(curinga);
            }
        }

        return where.toString();
    }

    private String marcas(int quantidade) {
        return String.join(", ", Collections.nCopies(quantidade, "?"));
    }

    private LocalDateTime instante(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
