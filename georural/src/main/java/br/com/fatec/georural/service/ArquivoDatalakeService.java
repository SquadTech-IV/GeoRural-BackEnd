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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;

// consulta e processamento de arquivos recebidos, lendo direto da zona BRUTA
@Service
public class ArquivoDatalakeService {

    private final ArquivoBrutoRepository arquivoRepository;
    private final ArmazenamentoZonaPort armazenamento;
    private final PipelineExecutor pipeline;

    public ArquivoDatalakeService(ArquivoBrutoRepository arquivoRepository,
                                  ArmazenamentoZonaPort armazenamento,
                                  PipelineExecutor pipeline) {
        this.arquivoRepository = arquivoRepository;
        this.armazenamento = armazenamento;
        this.pipeline = pipeline;
    }

    @Transactional(readOnly = true)
    public List<ArquivoResumoResponse> listar(String nome, String situacao) {
        String filtroNome = (nome != null && !nome.isBlank()) ? nome.trim().toLowerCase() : null;
        String filtroSituacao = (situacao != null && !situacao.isBlank()) ? situacao.trim().toUpperCase() : null;

        return arquivoRepository.findAll().stream()
                .filter(a -> filtroNome == null
                        || (a.getNome() != null && a.getNome().toLowerCase().contains(filtroNome)))
                .filter(a -> filtroSituacao == null || filtroSituacao.equals(a.getStatus()))
                .sorted(Comparator.comparing(ArquivoBruto::getDataUpload,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResumo)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArquivoDetalheResponse detalhe(Long id) {
        ArquivoBruto arquivo = buscar(id);
        ArquivoItemResponse item = new ArquivoItemResponse(
                arquivo.getId(), arquivo.getNome(), arquivo.getFormato(),
                arquivo.getTamanhoBytes(), null);
        return new ArquivoDetalheResponse(
                arquivo.getId(),
                nomeConjunto(arquivo),
                arquivo.getNome(),
                fonte(arquivo),
                arquivo.getFormato(),
                srid(arquivo),
                arquivo.getTamanhoBytes(),
                1,
                arquivo.getPathArmazenamento(),
                arquivo.getStatus(),
                null,
                arquivo.getDataUpload(),
                List.of(item));
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

        String mensagem = etapas.isEmpty()
                ? "Nenhuma etapa do pipeline foi executada."
                : etapas.size() + " etapa(s) executada(s), ultima: " + ultima.getEtapa()
                  + " (" + ultima.getStatus() + ")"
                  + (versao != null
                     ? ". Versao " + versao + " gerada como RASCUNHO: publique para os "
                       + "indicadores valerem."
                     : ". Nenhuma versao nova foi gerada.");

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

    private ArquivoResumoResponse toResumo(ArquivoBruto a) {
        return new ArquivoResumoResponse(
                a.getId(), nomeConjunto(a), a.getNome(), fonte(a),
                a.getFormato(), 1, a.getStatus(), a.getDataUpload());
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
