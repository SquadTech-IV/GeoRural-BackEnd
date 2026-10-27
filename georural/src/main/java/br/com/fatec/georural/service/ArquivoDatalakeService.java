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
import br.com.fatec.georural.service.treatment.PreparadorArquivoProcessamento;
import br.com.fatec.georural.service.treatment.orchestrator.TreatmentOrchestrator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;

/**
 * Consulta e processamento de arquivos recebidos, lendo direto da zona BRUTA do data lake
 */
@Service
public class ArquivoDatalakeService {

    private final ArquivoBrutoRepository arquivoRepository;
    private final ArmazenamentoZonaPort armazenamento;
    private final TreatmentOrchestrator orquestrador;
    private final PreparadorArquivoProcessamento preparador;

    public ArquivoDatalakeService(ArquivoBrutoRepository arquivoRepository,
                                  ArmazenamentoZonaPort armazenamento,
                                  TreatmentOrchestrator orquestrador,
                                  PreparadorArquivoProcessamento preparador) {
        this.arquivoRepository = arquivoRepository;
        this.armazenamento = armazenamento;
        this.orquestrador = orquestrador;
        this.preparador = preparador;
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

    /** Dispara o processamento real (pipeline de validacao/tratamento) do arquivo bruto. */
    @Transactional
    public EnvioProcessamentoResponse enviarParaProcessamento(Long id) {
        ArquivoBruto arquivo = buscar(id);

        if (!armazenamento.existe(Zona.BRUTA, arquivo.getPathArmazenamento())) {
            throw new ConflitoException(
                    "Arquivo sem conteudo na zona bruta; nao e possivel processar: " + id);
        }

        byte[] conteudo;
        try (InputStream in = armazenamento.ler(Zona.BRUTA, arquivo.getPathArmazenamento())) {
            conteudo = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo da zona bruta: " + id, e);
        }

        // Normaliza o arquivo antes do detector/tratamento: descompacta .zip (inclusive zip-dentro-de-zip
        // do SICAR) e, quando ha varios temas, escolhe o shapefile pelo tipo do conjunto. O diretorio
        // temporario e apagado ao final.
        String tipoConjunto = arquivo.getConjunto() != null ? arquivo.getConjunto().getTipoCamada() : null;
        PreparadorArquivoProcessamento.Preparacao preparacao =
                preparador.preparar(conteudo, arquivo.getNome(), tipoConjunto);
        try {
            ExecucaoPipeline execucao = orquestrador.processarValidacao(arquivo, preparacao.arquivo());

            return new EnvioProcessamentoResponse(
                    arquivo.getId(),
                    arquivo.getNome(),
                    arquivo.getFormato(),
                    srid(arquivo),
                    "/api/arquivos/" + id + "/download",
                    null, null, null,
                    arquivo.getPathArmazenamento(),
                    "Processamento concluido (execucao " + execucao.getId() + ").");
        } finally {
            preparador.limpar(preparacao.diretorio());
        }
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
