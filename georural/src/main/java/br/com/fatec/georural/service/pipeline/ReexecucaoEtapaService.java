package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.dto.request.pipeline.ReexecucaoEtapaRequest;
import br.com.fatec.georural.dto.response.pipeline.ContextoRecuperadoResponse;
import br.com.fatec.georural.dto.response.pipeline.EtapaExecutadaResponse;
import br.com.fatec.georural.dto.response.pipeline.ReexecucaoEtapaResponse;
import br.com.fatec.georural.dto.response.pipeline.TentativaAnteriorResponse;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// Reexecuta uma etapa de uma rodada existente
@Slf4j
@Service
public class ReexecucaoEtapaService {

    // Situacoes que ja terminaram e permitem reexecutar sem forcar.
    private static final List<String> PERMITEM_REEXECUCAO = List.of("FALHOU", "COM_RESSALVA");

    private final JdbcTemplate jdbc;
    private final PipelineExecutor executor;

    public ReexecucaoEtapaService(JdbcTemplate jdbc, PipelineExecutor executor) {
        this.jdbc = jdbc;
        this.executor = executor;
    }

    // Uma linha de EXECUCAO_PIPELINE da rodada, com o nome do conjunto e do arquivo.
    private record LinhaDaRodada(
            Long execucaoId,
            String etapa,
            String status,
            String mensagem,
            Long conjuntoId,
            String conjunto,
            Long arquivoBrutoId,
            String arquivo,
            Long usuarioId,
            LocalDateTime inicio,
            LocalDateTime fim,
            Long duracaoSeg
    ) {}

    public ReexecucaoEtapaResponse reexecutar(
            String dagRunId,
            Etapa etapa,
            ReexecucaoEtapaRequest pedido) {
        String rodadaId = dagRunId == null ? null : dagRunId.trim();

        if (rodadaId == null || rodadaId.isBlank()) {
            throw new RecursoNaoEncontradoException(
                    "Informe o dagRunId da rodada."
            );
        }

        List<LinhaDaRodada> rodada = lerRodada(rodadaId);

        if (rodada.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Rodada nao encontrada: " + rodadaId
            );
        }

        emAndamento(rodada).ifPresent(linha -> {
            throw new ConflitoException(
                    "A rodada " + rodadaId + " ainda esta rodando a etapa "
                            + linha.etapa() + " (execucao " + linha.execucaoId()
                            + ", iniciada em " + linha.inicio()
                            + "). Espere terminar antes de reexecutar."
            );
        });

        List<LinhaDaRodada> tentativas = rodada.stream()
                .filter(linha -> etapa.name().equals(linha.etapa()))
                .toList();

        LinhaDaRodada ultima = tentativas.isEmpty()
                ? null
                : tentativas.get(tentativas.size() - 1);

        boolean forcar = pedido != null && Boolean.TRUE.equals(pedido.forcar());

        if (ultima != null
                && !PERMITEM_REEXECUCAO.contains(ultima.status())
                && !forcar) {
            throw new ConflitoException(
                    "A etapa " + etapa + " da rodada " + rodadaId
                            + " esta como " + ultima.status()
                            + ". Reexecutar apagaria um resultado que deu certo; "
                            + "mande forcar=true se e isso que voce quer."
            );
        }

        ContextoExecucao ctx = montarContexto(rodadaId, etapa, rodada, ultima, pedido);

        log.info("[{}] reexecutando a etapa {} (tentativa {}), contexto recuperado "
                        + "do conjunto {} e do arquivo {}",
                rodadaId, etapa, tentativas.size() + 1,
                ctx.getConjuntoId(), ctx.getArquivoBrutoId());

        ExecucaoPipeline nova = executor.executarEtapa(etapa, ctx);

        return new ReexecucaoEtapaResponse(
                rodadaId,
                etapa.name(),
                tentativas.size() + 1,
                forcar,
                tentativaAnterior(ultima, tentativas.size()),
                contextoRecuperado(ctx, rodada, pedido),
                paraResponse(nova, etapa)
        );
    }

    private List<LinhaDaRodada> lerRodada(String dagRunId) {
        return jdbc.query("""
                SELECT e.exe_id,
                       e.exe_etapa,
                       e.exe_status,
                       e.exe_mensagem,
                       e.con_id,
                       c.con_nome,
                       e.arq_bruto_id,
                       a.arq_nome,
                       e.usu_id,
                       e.exe_inicio,
                       e.exe_fim,
                       e.exe_duracao_seg
                  FROM EXECUCAO_PIPELINE e
                  JOIN CONJUNTO_DADOS c     ON c.con_id = e.con_id
                  LEFT JOIN ARQUIVO_BRUTO a ON a.arq_bruto_id = e.arq_bruto_id
                 WHERE e.exe_dag_run_id = ?
                 ORDER BY e.exe_inicio, e.exe_id
                """,
                (rs, linha) -> new LinhaDaRodada(
                        rs.getLong("exe_id"),
                        rs.getString("exe_etapa"),
                        rs.getString("exe_status"),
                        rs.getString("exe_mensagem"),
                        rs.getObject("con_id", Long.class),
                        rs.getString("con_nome"),
                        rs.getObject("arq_bruto_id", Long.class),
                        rs.getString("arq_nome"),
                        rs.getObject("usu_id", Long.class),
                        instante(rs.getTimestamp("exe_inicio")),
                        instante(rs.getTimestamp("exe_fim")),
                        rs.getObject("exe_duracao_seg", Long.class)
                ),
                dagRunId
        );
    }

    private Optional<LinhaDaRodada> emAndamento(List<LinhaDaRodada> rodada) {
        return rodada.stream()
                .filter(linha -> "EM_ANDAMENTO".equals(linha.status()))
                .findFirst();
    }

    // Remonta o contexto: conjunto, arquivo, usuario, competencia e os parametros da rodada.
    private ContextoExecucao montarContexto(
            String dagRunId,
            Etapa etapa,
            List<LinhaDaRodada> rodada,
            LinhaDaRodada ultima,
            ReexecucaoEtapaRequest pedido) {
        Long conjuntoId = ultima != null && ultima.conjuntoId() != null
                ? ultima.conjuntoId()
                : ultimoNaoNulo(rodada, LinhaDaRodada::conjuntoId);

        if (conjuntoId == null) {
            throw new ConflitoException(
                    "A rodada " + dagRunId + " nao registrou conjunto, "
                            + "nao da para remontar o contexto."
            );
        }

        // O arquivo da propria etapa vale mais que o da rodada, quando ela ja rodou.
        Long arquivoBrutoId = ultima != null && ultima.arquivoBrutoId() != null
                ? ultima.arquivoBrutoId()
                : ultimoNaoNulo(rodada, LinhaDaRodada::arquivoBrutoId);

        Long usuarioId = pedido != null && pedido.usuarioId() != null
                ? pedido.usuarioId()
                : ultimoNaoNulo(rodada, LinhaDaRodada::usuarioId);

        ContextoExecucao ctx = ContextoExecucao.novo(conjuntoId, arquivoBrutoId, usuarioId);

        // A rodada e a mesma: e isso que mantem a trilha junta e a trava liberada.
        ctx.setDagRunId(dagRunId);

        Map<String, String> parametros = parametrosDaRodada(dagRunId);

        if (pedido != null && pedido.parametros() != null) {
            pedido.parametros().forEach((nome, valor) -> {
                if (nome != null && !nome.isBlank()) {
                    parametros.put(nome.trim(), valor);
                }
            });
        }

        String competencia = parametros.get("competencia");

        if (competencia != null && !competencia.isBlank()) {
            ctx.setCompetencia(competencia);
        }

        parametros.forEach(ctx::comParametro);

        return ctx;
    }

    // Todos os parametros da rodada; o valor mais recente de cada nome vence.
    private Map<String, String> parametrosDaRodada(String dagRunId) {
        Map<String, String> parametros = new LinkedHashMap<>();

        jdbc.query("""
                SELECT p.par_nome, p.par_valor
                  FROM PARAMETRO_EXECUCAO p
                  JOIN EXECUCAO_PIPELINE e ON e.exe_id = p.exe_id
                 WHERE e.exe_dag_run_id = ?
                 ORDER BY e.exe_inicio, e.exe_id, p.par_id
                """, rs -> {
            String nome = rs.getString("par_nome");
            String valor = rs.getString("par_valor");

            if (nome != null && valor != null) {
                parametros.put(nome, valor);
            }
        }, dagRunId);

        return parametros;
    }

    private <T> T ultimoNaoNulo(
            List<LinhaDaRodada> rodada,
            Function<LinhaDaRodada, T> campo) {
        T achado = null;

        for (LinhaDaRodada linha : rodada) {
            T valor = campo.apply(linha);

            if (valor != null) {
                achado = valor;
            }
        }

        return achado;
    }

    private TentativaAnteriorResponse tentativaAnterior(
            LinhaDaRodada ultima,
            int quantasTentativas) {
        if (ultima == null) {
            return null;
        }

        return new TentativaAnteriorResponse(
                ultima.execucaoId(),
                quantasTentativas,
                ultima.status(),
                ultima.mensagem(),
                ultima.inicio(),
                ultima.fim(),
                ultima.duracaoSeg()
        );
    }

    private ContextoRecuperadoResponse contextoRecuperado(
            ContextoExecucao ctx,
            List<LinhaDaRodada> rodada,
            ReexecucaoEtapaRequest pedido) {
        String conjunto = null;
        String arquivo = null;

        for (LinhaDaRodada linha : rodada) {
            if (conjunto == null && linha.conjunto() != null) {
                conjunto = linha.conjunto();
            }

            if (linha.arquivoBrutoId() != null
                    && linha.arquivoBrutoId().equals(ctx.getArquivoBrutoId())
                    && linha.arquivo() != null) {
                arquivo = linha.arquivo();
            }
        }

        List<String> informados = new ArrayList<>();

        if (pedido != null && pedido.parametros() != null) {
            pedido.parametros().keySet().stream()
                    .filter(nome -> nome != null && !nome.isBlank())
                    .map(String::trim)
                    .forEach(informados::add);
        }

        return new ContextoRecuperadoResponse(
                ctx.getConjuntoId(),
                conjunto,
                ctx.getArquivoBrutoId(),
                arquivo,
                ctx.getCompetencia(),
                ctx.getUsuarioId(),
                new LinkedHashMap<>(ctx.getParametros()),
                informados,
                rodada.size()
        );
    }

    private EtapaExecutadaResponse paraResponse(ExecucaoPipeline execucao, Etapa etapa) {
        // exe_ver_gerada_id vem por SQL: a associacao e LAZY e a entidade ja esta destacada.
        // query em vez de queryForObject para nao estourar se a linha nao aparecer.
        Long versaoGerada = jdbc.query(
                "SELECT exe_ver_gerada_id FROM EXECUCAO_PIPELINE WHERE exe_id = ?",
                rs -> rs.next() ? rs.getObject(1, Long.class) : null,
                execucao.getId()
        );

        return new EtapaExecutadaResponse(
                execucao.getId(),
                execucao.getEtapa(),
                etapa.ordem(),
                execucao.getDagRunId(),
                execucao.getStatus(),
                valor(execucao.getRegistrosRecebidos()),
                valor(execucao.getRegistrosAprovados()),
                valor(execucao.getRegistrosRejeitados()),
                execucao.getMensagem(),
                versaoGerada,
                execucao.getInicio(),
                execucao.getFim(),
                execucao.getDuracaoSeg()
        );
    }

    private long valor(Long numero) {
        return numero == null ? 0L : numero;
    }

    private LocalDateTime instante(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
