package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoDetalheResponse;
import br.com.fatec.georural.dto.response.ArquivoDownload;
import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.entity.ArquivoDatalake;
import br.com.fatec.georural.entity.ArquivoDatalakeConteudo;
import br.com.fatec.georural.entity.ArquivoDatalakeItem;
import br.com.fatec.georural.mapper.ArquivoDatalakeMapper;
import br.com.fatec.georural.repository.ArquivoDatalakeConteudoRepository;
import br.com.fatec.georural.repository.ArquivoDatalakeItemRepository;
import br.com.fatec.georural.repository.ArquivoDatalakeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ArquivoDatalakeService {

    private final ArquivoDatalakeRepository repository;
    private final ArquivoDatalakeItemRepository itemRepository;
    private final ArquivoDatalakeConteudoRepository conteudoRepository;
    private final ArquivoDatalakeMapper mapper;

    public ArquivoDatalakeService(ArquivoDatalakeRepository repository,
                                  ArquivoDatalakeItemRepository itemRepository,
                                  ArquivoDatalakeConteudoRepository conteudoRepository,
                                  ArquivoDatalakeMapper mapper) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.conteudoRepository = conteudoRepository;
        this.mapper = mapper;
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
        ArquivoDatalake arquivo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Arquivo nao encontrado: " + id));

        List<ArquivoDatalakeItem> itens = itemRepository.findByArquivo_IdOrderById(id);
        return mapper.toDetalhe(arquivo, itens);
    }

    public ArquivoDownload baixar(Long id) {
        ArquivoDatalake arquivo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Arquivo nao encontrado: " + id));

        ArquivoDatalakeConteudo conteudo = conteudoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Conteudo nao disponivel para o arquivo: " + id));

        return new ArquivoDownload(
                arquivo.getNomeArquivo(),
                conteudo.getContentType(),
                conteudo.getConteudo());
    }

    public void salvarConteudo(Long id, org.springframework.web.multipart.MultipartFile arquivo) {
        ArquivoDatalake meta = repository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Arquivo nao encontrado: " + id));

        try {
            ArquivoDatalakeConteudo c = new ArquivoDatalakeConteudo();
            c.setArquivoId(meta.getId());
            c.setContentType(arquivo.getContentType() != null ? arquivo.getContentType() : "application/zip");
            c.setConteudo(arquivo.getBytes());
            conteudoRepository.save(c);
        } catch (java.io.IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao ler o arquivo enviado");
        }
    }
}