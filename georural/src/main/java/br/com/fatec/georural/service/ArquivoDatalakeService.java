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
import br.com.fatec.georural.service.treatment.orchestrator.TreatmentOrchestrator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

@Service
public class ArquivoDatalakeService {

    private final ArquivoBrutoRepository arquivoRepository;
    private final ArmazenamentoZonaPort armazenamento;
    private final TreatmentOrchestrator orquestrador;

    public ArquivoDatalakeService(ArquivoBrutoRepository arquivoRepository,
                                  ArmazenamentoZonaPort armazenamento,
                                  TreatmentOrchestrator orquestrador) {
        this.arquivoRepository = arquivoRepository;
        this.armazenamento = armazenamento;
        this.orquestrador = orquestrador;
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

        Path temporario = null;
        try {
            temporario = Files.createTempFile("georural-processamento-", sufixoTemporario(arquivo));
            try (InputStream in = armazenamento.ler(Zona.BRUTA, arquivo.getPathArmazenamento())) {
                Files.copy(in, temporario, StandardCopyOption.REPLACE_EXISTING);
            }

            ExecucaoPipeline execucao = orquestrador.processarValidacao(arquivo, temporario.toFile());

            return new EnvioProcessamentoResponse(
                    arquivo.getId(),
                    arquivo.getNome(),
                    arquivo.getFormato(),
                    srid(arquivo),
                    "/api/arquivos/" + id + "/download",
                    null, null, null,
                    arquivo.getPathArmazenamento(),
                    "Processamento concluido (execucao " + execucao.getId() + ").");
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao preparar o arquivo para processamento: " + id, e);
        } finally {
            if (temporario != null) {
                try {
                    Files.deleteIfExists(temporario);
                } catch (IOException ignorado) {
                    // arquivo temporario; falha ao apagar nao afeta o resultado
                }
            }
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

    /**
     * Sufixo do arquivo temporario preservando a extensao original (ex.: ".geojson"). O GeoTools
     * ({@code FileDataStoreFinder}) escolhe o driver pela extensao, entao um ".tmp" impede a deteccao.
     */
    private static String sufixoTemporario(ArquivoBruto a) {
        String nome = a.getNome();
        if (nome != null) {
            int ponto = nome.lastIndexOf('.');
            if (ponto >= 0 && ponto < nome.length() - 1) {
                return nome.substring(ponto).toLowerCase();
            }
        }
        return ".tmp";
    }
}
