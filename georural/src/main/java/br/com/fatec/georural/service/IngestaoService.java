package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.dto.response.EnvioProcessamentoResponse;
import br.com.fatec.georural.entity.ArquivoDatalake;
import br.com.fatec.georural.entity.ArquivoDatalakeConteudo;
import br.com.fatec.georural.gateway.ProcessamentoGateway;
import br.com.fatec.georural.repository.ArquivoDatalakeConteudoRepository;
import br.com.fatec.georural.repository.ArquivoDatalakeRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class IngestaoService {

    private static final String TABELA_CONTEUDO = "ARQUIVO_DATALAKE_CONTEUDO";
    private static final String COLUNA_CONTEUDO = "conteudo";
    private static final String COLUNA_CHAVE    = "arquivo_id";

    private final IdentificadorFormatoService identificador;
    private final ArquivoDatalakeRepository arquivoRepository;
    private final ArquivoDatalakeConteudoRepository conteudoRepository;
    private final ProcessamentoGateway processamentoGateway;

    public IngestaoService(IdentificadorFormatoService identificador,
                           ArquivoDatalakeRepository arquivoRepository,
                           ArquivoDatalakeConteudoRepository conteudoRepository,
                           ProcessamentoGateway processamentoGateway) {
        this.identificador = identificador;
        this.arquivoRepository = arquivoRepository;
        this.conteudoRepository = conteudoRepository;
        this.processamentoGateway = processamentoGateway;
    }

    public ArquivoValidacaoResponse ingerir(MultipartFile arquivo) {
        String extensao = identificador.extrairExtensao(arquivo);
        String mime = identificador.detectarMimeType(arquivo);
        boolean aceito = identificador.isFormatoAceito(arquivo);

        // formato nao aceito -> para aqui e devolve o aviso (aceito = false), sem salvar
        if (!aceito) {
            return new ArquivoValidacaoResponse(
                    arquivo.getOriginalFilename(), extensao, mime, false);
        }

        try {
            byte[] bytes = arquivo.getBytes();

            Long id = salvarNoDataLake(arquivo, extensao, bytes);

            processamentoGateway.enviarParaProcessamento(montarEntrega(id, arquivo, extensao));

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

    private EnvioProcessamentoResponse montarEntrega(Long id, MultipartFile arquivo, String extensao) {
        return new EnvioProcessamentoResponse(
                id,
                arquivo.getOriginalFilename(),
                extensao.toUpperCase(),
                4674,
                "/api/arquivos/" + id + "/download",
                TABELA_CONTEUDO,
                COLUNA_CONTEUDO,
                COLUNA_CHAVE,
                "datalake/bruto/upload/" + arquivo.getOriginalFilename(),
                "Arquivo enviado para processamento.");
    }

    private String mapearFormato(String extensao) {
    return switch (extensao) {
        case "csv"            -> "CSV";
        case "json", "geojson"-> "GEOJSON";
        case "shp", "zip"     -> "SHAPEFILE";
        case "gpkg"           -> "GEOPACKAGE";
        case "tif"            -> "TIFF";
        default               -> "OUTRO";
    };
}
}