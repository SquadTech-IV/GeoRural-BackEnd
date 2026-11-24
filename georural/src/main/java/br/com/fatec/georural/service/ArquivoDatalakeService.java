package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoDetalheResponse;
import br.com.fatec.georural.dto.response.ArquivoDownload;
import br.com.fatec.georural.dto.response.ArquivoItemResponse;
import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.dto.response.EnvioProcessamentoResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.service.storage.ArmazenamentoZonaPort;
import br.com.fatec.georural.service.storage.Zona;
import br.com.fatec.georural.service.pipeline.ContextoExecucao;
import br.com.fatec.georural.service.pipeline.PipelineExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

// consulta e processamento de arquivos recebidos, lendo direto da zona BRUTA
@Service
public class ArquivoDatalakeService {

    // teto de linhas da tela; o filtro de nome e de situacao e quem estreita
    private static final int LIMITE_LISTA = 500;

    private final ArquivoBrutoRepository arquivoRepository;
    private final ArmazenamentoZonaPort armazenamento;
    private final PipelineExecutor pipeline;
    private final JdbcTemplate jdbc;

    public ArquivoDatalakeService(ArquivoBrutoRepository arquivoRepository,
                                  ArmazenamentoZonaPort armazenamento,
                                  PipelineExecutor pipeline,
                                  JdbcTemplate jdbc) {
        this.arquivoRepository = arquivoRepository;
        this.armazenamento = armazenamento;
        this.pipeline = pipeline;
        this.jdbc = jdbc;
    }

    // Lista so o arquivo que o usuario enviou, com quantas camadas vieram dentro.
    //
    // Antes isto era findAll() + filtro em memoria, sem olhar o arq_pai_id. Cada
    // camada de dentro do zip e uma linha propria em ARQUIVO_BRUTO, filha pelo
    // arq_pai_id, entao um CAR com 4 camadas aparecia como 5 arquivos e parecia
    // duplicado. O resto do sistema ja trata quem tem pai como "membro": o
    // ConsultaCatalogoService e o ConsumoRecursosService filtram arq_pai_id IS NULL.
    //
    // De passagem resolve outras duas coisas: o findAll() trazia a tabela inteira
    // para a memoria a cada abertura de tela, e o conjunto/fonte sao LAZY, o que
    // fazia uma consulta extra por linha para montar o resumo (N+1).
    @Transactional(readOnly = true)
    public List<ArquivoResumoResponse> listar(String nome, String situacao) {
        // O filtro entra na consulta so quando existe, em vez de "? IS NULL OR ...":
        // bind nulo comparado com IS NULL depende de o driver mandar o tipo e pode
        // dar ORA-00932. Assim a condicao simplesmente nao vai no SQL.
        StringBuilder onde = new StringBuilder(" WHERE a.arq_pai_id IS NULL");
        List<Object> parametros = new ArrayList<>();

        if (nome != null && !nome.isBlank()) {
            onde.append(" AND LOWER(a.arq_nome) LIKE ?");
            parametros.add("%" + nome.trim().toLowerCase() + "%");
        }
        if (situacao != null && !situacao.isBlank()) {
            onde.append(" AND a.arq_status = ?");
            parametros.add(situacao.trim().toUpperCase());
        }
        parametros.add(LIMITE_LISTA);

        String sql = """
                SELECT a.arq_bruto_id, c.con_nome, a.arq_nome, f.fon_nome,
                       a.arq_formato, a.arq_status, a.arq_data_upload,
                       (SELECT COUNT(*) FROM ARQUIVO_BRUTO m
                         WHERE m.arq_pai_id = a.arq_bruto_id) AS membros
                  FROM ARQUIVO_BRUTO a
                  LEFT JOIN CONJUNTO_DADOS c ON c.con_id = a.con_id
                  LEFT JOIN FONTE_DO_DADO f  ON f.fon_id = c.fon_id
                """
                + onde
                + " ORDER BY a.arq_data_upload DESC FETCH FIRST ? ROWS ONLY";

        return jdbc.query(sql, (rs, linha) -> {
            long membros = rs.getLong("membros");
            return new ArquivoResumoResponse(
                    rs.getLong("arq_bruto_id"),
                    rs.getString("con_nome"),
                    rs.getString("arq_nome"),
                    rs.getString("fon_nome"),
                    rs.getString("arq_formato"),
                    // quantos arquivos o envio trouxe: as camadas de dentro, ou ele mesmo
                    (int) Math.max(membros, 1),
                    rs.getString("arq_status"),
                    rs.getTimestamp("arq_data_upload") == null
                            ? null : rs.getTimestamp("arq_data_upload").toLocalDateTime());
        }, parametros.toArray());
    }

    @Transactional(readOnly = true)
    public ArquivoDetalheResponse detalhe(Long id) {
        ArquivoBruto arquivo = buscar(id);

        // as camadas que vieram dentro deste envio, para o detalhe mostrar o que
        // de fato chegou em vez de repetir o proprio arquivo
        List<ArquivoItemResponse> itens = new ArrayList<>(membros(id));
        if (itens.isEmpty()) {
            itens.add(new ArquivoItemResponse(
                    arquivo.getId(), arquivo.getNome(), arquivo.getFormato(),
                    arquivo.getTamanhoBytes(), null));
        }

        return new ArquivoDetalheResponse(
                arquivo.getId(),
                nomeConjunto(arquivo),
                arquivo.getNome(),
                fonte(arquivo),
                arquivo.getFormato(),
                srid(arquivo),
                arquivo.getTamanhoBytes(),
                itens.size(),
                arquivo.getPathArmazenamento(),
                arquivo.getStatus(),
                null,
                arquivo.getDataUpload(),
                itens);
    }

    @Transactional(readOnly = true)
    public ArquivoDownload baixar(Long id) {
        ArquivoBruto arquivo = buscar(id);
        try (InputStream in = armazenamento.ler(Zona.BRUTA, arquivo.getPathArmazenamento())) {
            byte[] bytes = in.readAllBytes();
            return new ArquivoDownload(arquivo.getNome(), contentType(arquivo), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo da zona bruta: " + id, e);
        }
    }

    // roda as etapas 1 a 5 do pipeline para o arquivo
    @Transactional
    public EnvioProcessamentoResponse enviarParaProcessamento(Long id) {
        ArquivoBruto arquivo = buscar(id);

        if (!armazenamento.existe(Zona.BRUTA, arquivo.getPathArmazenamento())) {
            throw new ConflitoException(
                    "Arquivo sem conteudo na zona bruta; nao e possivel processar: " + id);
        }

        ConjuntoDados conjunto = arquivo.getConjunto();
        if (conjunto == null) {
            throw new ConflitoException(
                    "Arquivo " + id + " nao esta ligado a um conjunto de dados.");
        }

        ContextoExecucao ctx = ContextoExecucao.novo(
                conjunto.getId(), arquivo.getId(),
                arquivo.getUsuario() != null ? arquivo.getUsuario().getId() : null);
        ctx.setCompetencia(arquivo.getCompetencia());

        List<ExecucaoPipeline> etapas = pipeline.executarAutomatico(ctx);

        ExecucaoPipeline ultima = etapas.isEmpty() ? null : etapas.get(etapas.size() - 1);
        Long versao = null;
        for (ExecucaoPipeline etapa : etapas) {
            if (etapa.getVersaoGerada() != null) {
                versao = etapa.getVersaoGerada().getId();
            }
        }

        // Quando a rodada termina sem versao, a mensagem da propria etapa diz o motivo
        // ("Nenhuma feicao passou pelo tratamento. Confira a quarentena: ..."). Sem isso
        // a tela mostrava so "nenhuma versao nova foi gerada", que nao ajuda ninguem.
        String motivo = (ultima != null && ultima.getMensagem() != null
                && !ultima.getMensagem().isBlank())
                ? " " + ultima.getMensagem().trim()
                : "";

        String mensagem = etapas.isEmpty()
                ? "Nenhuma etapa do pipeline foi executada."
                : etapas.size() + " etapa(s) executada(s), ultima: " + ultima.getEtapa()
                + " (" + ultima.getStatus() + ")"
                + (versao != null
                ? ". Versao " + versao + " gerada como RASCUNHO: publique para os "
                + "indicadores valerem."
                : ". Nenhuma versao nova foi gerada." + motivo);

        return new EnvioProcessamentoResponse(
                arquivo.getId(),
                arquivo.getNome(),
                arquivo.getFormato(),
                srid(arquivo),
                "/api/arquivos/" + id + "/download",
                null, null, null,
                arquivo.getPathArmazenamento(),
                mensagem);
    }

    // camadas filhas deste envio, na ordem em que entraram
    private List<ArquivoItemResponse> membros(Long paiId) {
        return jdbc.query("""
                SELECT arq_bruto_id, arq_nome, arq_formato, arq_tamanho_bytes, arq_caminho_interno
                  FROM ARQUIVO_BRUTO
                 WHERE arq_pai_id = ?
                 ORDER BY arq_bruto_id
                """,
                (rs, linha) -> new ArquivoItemResponse(
                        rs.getLong("arq_bruto_id"),
                        rs.getString("arq_nome"),
                        rs.getString("arq_formato"),
                        rs.getObject("arq_tamanho_bytes", Long.class),
                        rs.getString("arq_caminho_interno")),
                paiId);
    }

    private ArquivoBruto buscar(Long id) {
        return arquivoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Arquivo nao encontrado: " + id));
    }

    private static String nomeConjunto(ArquivoBruto a) {
        return a.getConjunto() != null ? a.getConjunto().getNome() : null;
    }

    private static String fonte(ArquivoBruto a) {
        ConjuntoDados c = a.getConjunto();
        return (c != null && c.getFonte() != null) ? c.getFonte().getNome() : null;
    }

    private static Integer srid(ArquivoBruto a) {
        return (a.getConjunto() != null) ? a.getConjunto().getSrs() : null;
    }

    private static String contentType(ArquivoBruto a) {
        String f = a.getFormato();
        if (f == null) {
            return "application/octet-stream";
        }
        return switch (f.toUpperCase()) {
            case "GEOJSON" -> "application/geo+json";
            case "CSV" -> "text/csv";
            default -> "application/octet-stream";
        };
    }
}