package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoDetalheResponse;
import br.com.fatec.georural.dto.response.ArquivoDownload;
import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.dto.response.EnvioProcessamentoResponse;
import br.com.fatec.georural.entity.ArquivoDatalake;
import br.com.fatec.georural.entity.ArquivoDatalakeConteudo;
import br.com.fatec.georural.entity.ArquivoDatalakeItem;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.gateway.ProcessamentoGateway;
import br.com.fatec.georural.mapper.ArquivoDatalakeMapper;
import br.com.fatec.georural.repository.ArquivoDatalakeConteudoRepository;
import br.com.fatec.georural.repository.ArquivoDatalakeItemRepository;
import br.com.fatec.georural.repository.ArquivoDatalakeRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@Service
public class ArquivoDatalakeService {

    private static final String TABELA_CONTEUDO = "ARQUIVO_DATALAKE_CONTEUDO";
    private static final String COLUNA_CONTEUDO = "conteudo";
    private static final String COLUNA_CHAVE    = "arquivo_id";

    private final ArquivoDatalakeRepository repository;
    private final ArquivoDatalakeItemRepository itemRepository;
    private final ArquivoDatalakeConteudoRepository conteudoRepository;
    private final ArquivoDatalakeMapper mapper;
    private final ProcessamentoGateway processamentoGateway;

    public ArquivoDatalakeService(ArquivoDatalakeRepository repository,
                                  ArquivoDatalakeItemRepository itemRepository,
                                  ArquivoDatalakeConteudoRepository conteudoRepository,
                                  ArquivoDatalakeMapper mapper,
                                  ProcessamentoGateway processamentoGateway) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.conteudoRepository = conteudoRepository;
        this.mapper = mapper;
        this.processamentoGateway = processamentoGateway;
    }

    public List<ArquivoResumoResponse> listar(String nome, String situacao) {
        String filtroNome = (nome != null && !nome.isBlank()) ? nome.trim() : null;
        String filtroSituacao = (situacao != null && !situacao.isBlank())
                ? situacao.trim().toUpperCase() : null;

        return repository.buscar(filtroNome, filtroSituacao)
                .stream()
                .map(mapper::toResumo)
                .toList();
    }

    public ArquivoDetalheResponse detalhe(Long id) {
        ArquivoDatalake arquivo = buscarArquivo(id);
        List<ArquivoDatalakeItem> itens = itemRepository.findByArquivo_IdOrderById(id);
        return mapper.toDetalhe(arquivo, itens);
    }

    public ArquivoDownload baixar(Long id) {
        ArquivoDatalake arquivo = buscarArquivo(id);

        ArquivoDatalakeConteudo conteudo = conteudoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Conteudo nao disponivel para o arquivo: " + id));

        return new ArquivoDownload(
                arquivo.getNomeArquivo(),
                conteudo.getContentType(),
                conteudo.getConteudo());
    }

    public void salvarConteudo(Long id, MultipartFile arquivo) {
        ArquivoDatalake meta = buscarArquivo(id);
        try {
            ArquivoDatalakeConteudo c = new ArquivoDatalakeConteudo();
            c.setArquivoId(meta.getId());
            c.setContentType(arquivo.getContentType() != null
                    ? arquivo.getContentType() : "application/zip");
            c.setConteudo(arquivo.getBytes());
            conteudoRepository.save(c);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao ler o arquivo enviado", e);
        }
    }

    public EnvioProcessamentoResponse enviarParaProcessamento(Long id) {
        ArquivoDatalake arquivo = buscarArquivo(id);

        if (!conteudoRepository.existsById(id)) {
            throw new ConflitoException(
                    "Arquivo sem conteudo salvo; nao e possivel enviar para processamento: " + id);
        }

        EnvioProcessamentoResponse entrega = new EnvioProcessamentoResponse(
                arquivo.getId(),
                arquivo.getNomeArquivo(),
                arquivo.getFormato(),
                arquivo.getSrid(),
                "/api/arquivos/" + id + "/download",
                TABELA_CONTEUDO,
                COLUNA_CONTEUDO,
                COLUNA_CHAVE,
                arquivo.getCaminho(),
                "Arquivo pronto para processamento.");

        processamentoGateway.enviarParaProcessamento(entrega);
        return entrega;
    }

    private ArquivoDatalake buscarArquivo(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Arquivo nao encontrado: " + id));
    }
}