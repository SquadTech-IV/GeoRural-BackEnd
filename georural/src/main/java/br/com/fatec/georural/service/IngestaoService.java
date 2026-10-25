package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.entity.ArquivoDatalake;
import br.com.fatec.georural.entity.ArquivoDatalakeConteudo;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ArquivoDatalakeConteudoRepository;
import br.com.fatec.georural.repository.ArquivoDatalakeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class IngestaoService {

    private final IdentificadorFormatoService identificador;
    private final ArquivoDatalakeRepository arquivoRepository;
    private final ArquivoDatalakeConteudoRepository conteudoRepository;
    private final OrigemArquivoService origemService;

    public IngestaoService(
        IdentificadorFormatoService identificador,
        ArquivoDatalakeRepository arquivoRepository,
        ArquivoDatalakeConteudoRepository conteudoRepository,
        OrigemArquivoService origemService
    ) {
        this.identificador = identificador;
        this.arquivoRepository = arquivoRepository;
        this.conteudoRepository = conteudoRepository;
        this.origemService = origemService;
    }

    @Transactional
    public List<ArquivoValidacaoResponse> ingerir(
        List<MultipartFile> arquivos,
        Long conjuntoId
    ) {
        validarArquivos(arquivos);

        ConjuntoDados conjunto = origemService
            .obterConjuntoParaUpload(conjuntoId);

        List<ArquivoValidacaoResponse> resultados =
            new ArrayList<>();

        for (MultipartFile arquivo : arquivos) {
            String extensao = identificador.extrairExtensao(
                arquivo
            );

            String mime = identificador.detectarMimeType(
                arquivo
            );

            boolean aceito = identificador.isFormatoAceito(
                arquivo
            );

            Long arquivoId = null;

            if (aceito) {
                arquivoId = salvar(
                    arquivo,
                    extensao,
                    conjunto
                );
            }

            resultados.add(
                new ArquivoValidacaoResponse(
                    arquivo.getOriginalFilename(),
                    extensao,
                    mime,
                    aceito,
                    arquivoId,
                    conjunto.getFonte().getId(),
                    conjunto.getId()
                )
            );
        }

        return resultados;
    }

    private void validarArquivos(List<MultipartFile> arquivos) {
        if (arquivos == null || arquivos.isEmpty()) {
            throw new RequisicaoInvalidaException(
                "Envie pelo menos um arquivo."
            );
        }

        boolean possuiArquivoVazio = arquivos.stream()
            .anyMatch(arquivo ->
                arquivo == null || arquivo.isEmpty()
            );

        if (possuiArquivoVazio) {
            throw new RequisicaoInvalidaException(
                "A lista contém um arquivo vazio."
            );
        }
    }

    private Long salvar(
        MultipartFile arquivo,
        String extensao,
        ConjuntoDados conjunto
    ) {
        try {
            byte[] bytes = arquivo.getBytes();
            String nome = obterNomeArquivo(arquivo);

            ArquivoDatalake metadata = new ArquivoDatalake();

            metadata.setNome(nome);
            metadata.setNomeArquivo(nome);
            metadata.setConjunto(conjunto);
            metadata.setFonte(
                conjunto.getFonte().getNome()
            );
            metadata.setTipoCamada(
                conjunto.getTipoCamada()
            );
            metadata.setSrid(
                conjunto.getSrs()
            );
            metadata.setCompetenciaOrigem(
                conjunto.getCompetencia()
            );
            metadata.setCoberturaOrigem(
                conjunto.getCobertura()
            );
            metadata.setFormato(
                mapearFormato(extensao)
            );
            metadata.setTamanhoBytes(
                (long) bytes.length
            );
            metadata.setQtdArquivos(1);
            metadata.setSituacao("AGUARDANDO");
            metadata.setRecebidoEm(
                LocalDateTime.now()
            );
            metadata.setCaminho(
                "datalake/bruto/" +
                conjunto.getId() + "/" +
                UUID.randomUUID() + "/" +
                nome
            );

            metadata = arquivoRepository.saveAndFlush(
                metadata
            );

            ArquivoDatalakeConteudo conteudo =
                new ArquivoDatalakeConteudo();

            conteudo.setArquivoId(metadata.getId());
            conteudo.setConteudo(bytes);
            conteudo.setContentType(
                arquivo.getContentType() == null
                    ? "application/octet-stream"
                    : arquivo.getContentType()
            );

            conteudoRepository.save(conteudo);

            return metadata.getId();
        } catch (IOException erro) {
            throw new IllegalStateException(
                "Não foi possível ler o arquivo enviado.",
                erro
            );
        }
    }

    private String obterNomeArquivo(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename();

        if (nome == null || nome.isBlank()) {
            throw new RequisicaoInvalidaException(
                "O arquivo precisa possuir um nome."
            );
        }

        nome = nome.replace('\\', '/');
        nome = nome.substring(nome.lastIndexOf('/') + 1);

        if (
            nome.isBlank() ||
            nome.equals(".") ||
            nome.equals("..") ||
            nome.length() > 255
        ) {
            throw new RequisicaoInvalidaException(
                "O nome do arquivo é inválido ou muito longo."
            );
        }

        return nome;
    }
    
    public ArquivoValidacaoResponse ingerir(MultipartFile arquivo) {
        String extensao = identificador.extrairExtensao(arquivo);
        String mime = identificador.detectarMimeType(arquivo);
        boolean aceito = identificador.isFormatoAceito(arquivo);


        if (!aceito) {
            return new ArquivoValidacaoResponse(
                    arquivo.getOriginalFilename(), extensao, mime, false);
        }

        try {
            byte[] bytes = arquivo.getBytes();
            
            salvarNoDataLake(arquivo, extensao, bytes);

            return new ArquivoValidacaoResponse(
                    arquivo.getOriginalFilename(), extensao, mime, true);

        } catch (IOException e) {
            throw new RuntimeException("Falha ao ler o arquivo enviado", e);
        }
    }

    private Long salvarNoDataLake(MultipartFile arquivo, String extensao, byte[] bytes) {
        ArquivoDatalake meta = new ArquivoDatalake();
        meta.setNome(arquivo.getOriginalFilename());
        meta.setNomeArquivo(arquivo.getOriginalFilename());
        meta.setFonte("UPLOAD");
        meta.setFormato(mapearFormato(extensao));
        meta.setTamanhoBytes((long) bytes.length);
        meta.setQtdArquivos(1);
        meta.setSituacao("AGUARDANDO");
        meta.setCaminho("datalake/bruto/upload/" + arquivo.getOriginalFilename());
        meta.setRecebidoEm(java.time.LocalDateTime.now());
        meta = arquivoRepository.save(meta);

        ArquivoDatalakeConteudo c = new ArquivoDatalakeConteudo();
        c.setArquivoId(meta.getId());
        c.setContentType(arquivo.getContentType() != null
                ? arquivo.getContentType() : "application/octet-stream");
        c.setConteudo(bytes);
        conteudoRepository.save(c);

        return meta.getId();
    }

    private String mapearFormato(String extensao) {
        return switch (extensao) {
            case "csv"             -> "CSV";
            case "json", "geojson" -> "GEOJSON";
            case "shp", "zip"      -> "SHAPEFILE";
            case "gpkg"            -> "GEOPACKAGE";
            case "tif"             -> "TIFF";
            default                -> "OUTRO";
        };
    }
}