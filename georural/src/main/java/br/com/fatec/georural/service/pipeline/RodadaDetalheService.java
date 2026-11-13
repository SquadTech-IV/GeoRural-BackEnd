package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.dto.response.pipeline.EtapaDaRodadaResponse;
import br.com.fatec.georural.dto.response.pipeline.RodadaDetalheResponse;
import br.com.fatec.georural.dto.response.pipeline.TentativaEtapaResponse;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Detalhe de uma rodada do pipeline, com as tentativas de cada etapa
@Service
public class RodadaDetalheService {

    // Situacoes terminadas que permitem reexecutar sem forcar. Mesma lista da US#10.01.
    private static final List<String> PERMITEM_REEXECUCAO = List.of("FALHOU", "COM_RESSALVA");

    // Etapa que esta no CHECK do banco mas nao no fluxo das 6.
    private static final int ORDEM_FORA_DO_FLUXO = 9;

    private final JdbcTemplate jdbc;
    private final PipelineExecutor executor;

    public RodadaDetalheService(JdbcTemplate jdbc, PipelineExecutor executor) {
        this.jdbc = jdbc;
        this.executor = executor;
    }

    // Uma linha de EXECUCAO_PIPELINE da rodada.
    private record LinhaDaRodada(
            Long execucaoId,
            String etapa,
            String status,
            String mensagem,
            Long conjuntoId,
            String conjunto,
            String tipoCamada,
            String fonte,
            Long arquivoBrutoId,
            String arquivo,
            String competencia,
            Long usuarioId,
            String disparadoPor,
            long recebidos,
            long aprovados,
            long rejeitados,
            Long versaoGeradaId,
            LocalDateTime inicio,
            LocalDateTime fim,
            Long duracaoSeg
    ) {}

    public RodadaDetalheResponse detalhar(String dagRunId) {
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

        boolean algumaEmAndamento = rodada.stream()
                .anyMatch(linha -> "EM_ANDAMENTO".equals(linha.status()));

        List<EtapaDaRodadaResponse> etapas =
                montarEtapas(rodada, algumaEmAndamento);

        return new RodadaDetalheResponse(
                rodadaId,
                primeiro(rodada, LinhaDaRodada::conjuntoId),
                primeiro(rodada, LinhaDaRodada::conjunto),
                primeiro(rodada, LinhaDaRodada::tipoCamada),
                primeiro(rodada, LinhaDaRodada::fonte),
                ultimo(rodada, LinhaDaRodada::arquivoBrutoId),
                ultimo(rodada, LinhaDaRodada::arquivo),
                ultimo(rodada, LinhaDaRodada::competencia),
                automatica(rodadaId, rodada),
                ultimo(rodada, LinhaDaRodada::disparadoPor),
                situacaoDaRodada(rodada),
                etapaAtual(rodada),
                (int) etapas.stream().filter(EtapaDaRodadaResponse::alcancada).count(),
                rodada.size(),
                reexecucoes(rodada),
                recebidosDaRodada(rodada),
                rejeitadosDaRodada(rodada),
                ultimo(rodada, LinhaDaRodada::versaoGeradaId),
                inicio(rodada),
                fim(rodada, algumaEmAndamento),
                duracao(rodada),
                etapas
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
                       t.tip_codigo,
                       f.fon_sigla,
                       e.arq_bruto_id,
                       a.arq_nome,
                       a.arq_competencia,
                       e.usu_id,
                       u.usu_nome,
                       e.exe_registros_recebidos,
                       e.exe_registros_aprovados,
                       e.exe_registros_rejeitados,
                       e.exe_ver_gerada_id,
                       e.exe_inicio,
                       e.exe_fim,
                       e.exe_duracao_seg
                  FROM EXECUCAO_PIPELINE e
                  JOIN CONJUNTO_DADOS c     ON c.con_id = e.con_id
                  JOIN TIPO_CONJUNTO t      ON t.tip_id = c.tip_id
                  JOIN FONTE_DO_DADO f      ON f.fon_id = c.fon_id
                  LEFT JOIN ARQUIVO_BRUTO a ON a.arq_bruto_id = e.arq_bruto_id
                  LEFT JOIN USUARIO u       ON u.usu_id = e.usu_id
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
                        rs.getString("tip_codigo"),
                        rs.getString("fon_sigla"),
                        rs.getObject("arq_bruto_id", Long.class),
                        rs.getString("arq_nome"),
                        rs.getString("arq_competencia"),
                        rs.getObject("usu_id", Long.class),
                        rs.getString("usu_nome"),
                        rs.getLong("exe_registros_recebidos"),
                        rs.getLong("exe_registros_aprovados"),
                        rs.getLong("exe_registros_rejeitados"),
                        rs.getObject("exe_ver_gerada_id", Long.class),
                        instante(rs.getTimestamp("exe_inicio")),
                        instante(rs.getTimestamp("exe_fim")),
                        rs.getObject("exe_duracao_seg", Long.class)
                ),
                dagRunId
        );
    }

    // Uma entrada por etapa do fluxo, mesmo as que nao rodaram, mais o que veio fora dele.
    private List<EtapaDaRodadaResponse> montarEtapas(
            List<LinhaDaRodada> rodada,
            boolean algumaEmAndamento) {
        Map<String, List<LinhaDaRodada>> porEtapa = new LinkedHashMap<>();

        for (LinhaDaRodada linha : rodada) {
            porEtapa.computeIfAbsent(linha.etapa(), chave -> new ArrayList<>()).add(linha);
        }

        List<EtapaDaRodadaResponse> etapas = new ArrayList<>();

        for (Etapa etapa : Etapa.values()) {
            etapas.add(montarEtapa(
                    etapa.name(),
                    etapa.ordem(),
                    etapa.descricao(),
                    executor.implementada(etapa),
                    porEtapa.remove(etapa.name()),
                    algumaEmAndamento
            ));
        }

        // Sobrou alguma etapa fora das 6 (o CHECK do banco aceita CADASTRO e CONSUMO).
        porEtapa.forEach((nome, tentativas) -> etapas.add(montarEtapa(
                nome,
                ORDEM_FORA_DO_FLUXO,
                "Etapa fora do fluxo das 6",
                false,
                tentativas,
                algumaEmAndamento
        )));

        return etapas;
    }

    private EtapaDaRodadaResponse montarEtapa(
            String nome,
            int ordem,
            String descricao,
            boolean implementada,
            List<LinhaDaRodada> tentativas,
            boolean algumaEmAndamento) {
        if (tentativas == null || tentativas.isEmpty()) {
            return new EtapaDaRodadaResponse(
                    nome, ordem, descricao, implementada,
                    false, null, 0, false, false, null, List.of());
        }

        List<TentativaEtapaResponse> historico = new ArrayList<>();

        for (int i = 0; i < tentativas.size(); i++) {
            historico.add(paraTentativa(tentativas.get(i), i + 1));
        }

        TentativaEtapaResponse ultima = historico.get(historico.size() - 1);

        // Mesma regra da US#10.01: nada rodando na rodada e a ultima tentativa terminou mal.
        boolean podeReexecutar = !algumaEmAndamento
                && PERMITEM_REEXECUCAO.contains(ultima.situacao());

        boolean exigeForcar = !algumaEmAndamento
                && !PERMITEM_REEXECUCAO.contains(ultima.situacao());

        return new EtapaDaRodadaResponse(
                nome,
                ordem,
                descricao,
                implementada,
                true,
                ultima.situacao(),
                historico.size(),
                podeReexecutar,
                exigeForcar,
                ultima,
                historico
        );
    }

    private TentativaEtapaResponse paraTentativa(LinhaDaRodada linha, int numero) {
        return new TentativaEtapaResponse(
                linha.execucaoId(),
                numero,
                linha.status(),
                linha.mensagem(),
                linha.disparadoPor(),
                linha.recebidos(),
                linha.aprovados(),
                linha.rejeitados(),
                linha.versaoGeradaId(),
                linha.inicio(),
                linha.fim(),
                linha.duracaoSeg()
        );
    }

    // FALHOU vence tudo; depois EM_ANDAMENTO; depois COM_RESSALVA; senao CONCLUIDA.
    private String situacaoDaRodada(List<LinhaDaRodada> rodada) {
        List<String> situacoesFinais = situacoesFinais(rodada);

        if (situacoesFinais.contains("FALHOU")) {
            return "FALHOU";
        }

        if (situacoesFinais.contains("EM_ANDAMENTO")) {
            return "EM_ANDAMENTO";
        }

        if (situacoesFinais.contains("COM_RESSALVA")) {
            return "COM_RESSALVA";
        }

        return "CONCLUIDA";
    }

    // So a ultima tentativa de cada etapa conta: uma falha ja reexecutada com sucesso
    // nao deixa a rodada inteira como FALHOU.
    private List<String> situacoesFinais(List<LinhaDaRodada> rodada) {
        Map<String, String> ultimaPorEtapa = new LinkedHashMap<>();

        for (LinhaDaRodada linha : rodada) {
            ultimaPorEtapa.put(linha.etapa(), linha.status());
        }

        return new ArrayList<>(ultimaPorEtapa.values());
    }

    // A etapa que esta rodando; senao a que falhou; senao a ultima que rodou.
    private String etapaAtual(List<LinhaDaRodada> rodada) {
        String emAndamento = null;
        String falhou = null;

        for (LinhaDaRodada linha : rodada) {
            if ("EM_ANDAMENTO".equals(linha.status())) {
                emAndamento = linha.etapa();
            }
        }

        Map<String, String> ultimaPorEtapa = new LinkedHashMap<>();

        for (LinhaDaRodada linha : rodada) {
            ultimaPorEtapa.put(linha.etapa(), linha.status());
        }

        for (Map.Entry<String, String> entrada : ultimaPorEtapa.entrySet()) {
            if ("FALHOU".equals(entrada.getValue())) {
                falhou = entrada.getKey();
                break;
            }
        }

        if (emAndamento != null) {
            return emAndamento;
        }

        if (falhou != null) {
            return falhou;
        }

        return rodada.get(rodada.size() - 1).etapa();
    }

    // Quantas linhas passaram do primeiro acerto de cada etapa.
    private int reexecucoes(List<LinhaDaRodada> rodada) {
        long etapasDistintas = rodada.stream()
                .map(LinhaDaRodada::etapa)
                .distinct()
                .count();

        return (int) (rodada.size() - etapasDistintas);
    }

    // Recebidos e o que a primeira etapa com numero informou.
    private long recebidosDaRodada(List<LinhaDaRodada> rodada) {
        for (LinhaDaRodada linha : rodada) {
            if (linha.recebidos() > 0) {
                return linha.recebidos();
            }
        }

        return 0L;
    }

    // Rejeitados somam: rejeicao na validacao e no tratamento sao rejeicoes diferentes.
    private long rejeitadosDaRodada(List<LinhaDaRodada> rodada) {
        long total = 0;

        for (String etapa : etapasNaOrdemDaTrilha(rodada)) {
            total += ultimaTentativa(rodada, etapa).rejeitados();
        }

        return total;
    }

    private List<String> etapasNaOrdemDaTrilha(List<LinhaDaRodada> rodada) {
        List<String> nomes = new ArrayList<>();

        for (LinhaDaRodada linha : rodada) {
            if (!nomes.contains(linha.etapa())) {
                nomes.add(linha.etapa());
            }
        }

        return nomes;
    }

    private LinhaDaRodada ultimaTentativa(List<LinhaDaRodada> rodada, String etapa) {
        LinhaDaRodada achada = null;

        for (LinhaDaRodada linha : rodada) {
            if (etapa.equals(linha.etapa())) {
                achada = linha;
            }
        }

        return achada;
    }

    private LocalDateTime inicio(List<LinhaDaRodada> rodada) {
        LocalDateTime menor = null;

        for (LinhaDaRodada linha : rodada) {
            if (linha.inicio() != null && (menor == null || linha.inicio().isBefore(menor))) {
                menor = linha.inicio();
            }
        }

        return menor;
    }

    // Rodada com etapa rodando nao tem fim.
    private LocalDateTime fim(List<LinhaDaRodada> rodada, boolean algumaEmAndamento) {
        if (algumaEmAndamento) {
            return null;
        }

        LocalDateTime maior = null;

        for (LinhaDaRodada linha : rodada) {
            if (linha.fim() != null && (maior == null || linha.fim().isAfter(maior))) {
                maior = linha.fim();
            }
        }

        return maior;
    }

    private Long duracao(List<LinhaDaRodada> rodada) {
        long total = 0;
        boolean algum = false;

        for (LinhaDaRodada linha : rodada) {
            if (linha.duracaoSeg() != null) {
                total += linha.duracaoSeg();
                algum = true;
            }
        }

        return algum ? total : null;
    }

    // Mesma regra do ExecucaoConsultaService: rodada do portal ou com usuario e manual.
    private boolean automatica(String dagRunId, List<LinhaDaRodada> rodada) {
        if (dagRunId.startsWith("portal-")) {
            return false;
        }

        return rodada.stream().noneMatch(linha -> linha.usuarioId() != null);
    }

    private <T> T primeiro(
            List<LinhaDaRodada> rodada,
            Function<LinhaDaRodada, T> campo) {
        for (LinhaDaRodada linha : rodada) {
            T valor = campo.apply(linha);

            if (valor != null) {
                return valor;
            }
        }

        return null;
    }

    private <T> T ultimo(
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

    private LocalDateTime instante(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
