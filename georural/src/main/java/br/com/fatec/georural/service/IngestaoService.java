package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.service.storage.RecebimentoArquivoService;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingestao por upload manual. Grava direto na zona BRUTA do data lake
 * (ARQUIVO_BRUTO + armazenamento de objetos) via RecebimentoArquivoService
 */
@Service
public class IngestaoService {

    private final IdentificadorFormatoService identificador;
    private final OrigemArquivoService origemService;
    private final RecebimentoArquivoService recebimentoService;

    public IngestaoService(
        IdentificadorFormatoService identificador,
        OrigemArquivoService origemService,
        RecebimentoArquivoService recebimentoService
    ) {
        this.identificador = identificador;
        this.origemService = origemService;
        this.recebimentoService = recebimentoService;
    }

    public List<ArquivoValidacaoResponse> ingerir(List<MultipartFile> arquivos, Long conjuntoId) {
        validarArquivos(arquivos);

        ConjuntoDados conjunto = origemService.obterConjuntoParaUpload(conjuntoId);
        Long usuarioId = conjunto.getUsuario() != null ? conjunto.getUsuario().getId() : null;
        Long fonteId = conjunto.getFonte() != null ? conjunto.getFonte().getId() : null;

        List<ArquivoValidacaoResponse> resultados = new ArrayList<>();

        for (MultipartFile arquivo : arquivos) {
            String extensao = identificador.extrairExtensao(arquivo);
            String mime = identificador.detectarMimeType(arquivo);
            boolean aceito = identificador.isFormatoAceito(arquivo);

            Long arquivoId = null;
            if (aceito) {
                try (InputStream in = arquivo.getInputStream()) {
                    ArquivoBruto registro = recebimentoService.receber(
                            conjuntoId, usuarioId, arquivo.getOriginalFilename(), in);
                    arquivoId = registro.getId();
                } catch (IOException e) {
                    throw new IllegalStateException("Nao foi possivel ler o arquivo enviado.", e);
                } catch (RuntimeException e) {
                    // formato/conteudo invalido, duplicidade ou conjunto incompativel:
                    // o arquivo nao entra na zona bruta e e marcado como nao aceito.
                    aceito = false;
                }
            }

            resultados.add(new ArquivoValidacaoResponse(
                    arquivo.getOriginalFilename(), extensao, mime, aceito,
                    arquivoId, fonteId, conjunto.getId()));
        }

        return resultados;
    }

    private void validarArquivos(List<MultipartFile> arquivos) {
        if (arquivos == null || arquivos.isEmpty()) {
            throw new RequisicaoInvalidaException("Envie pelo menos um arquivo.");
        }
        boolean possuiVazio = arquivos.stream().anyMatch(a -> a == null || a.isEmpty());
        if (possuiVazio) {
            throw new RequisicaoInvalidaException("A lista contém um arquivo vazio.");
        }
    }
}
