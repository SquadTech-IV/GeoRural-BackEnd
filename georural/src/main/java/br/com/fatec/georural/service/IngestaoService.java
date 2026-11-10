package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.ArquivoDuplicadoException;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.service.ingestao.SituacaoEnvio;
import br.com.fatec.georural.service.storage.RecebimentoArquivoService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingestao por upload manual, um ou varios arquivos na mesma chamada. Grava direto na
 * zona BRUTA do data lake (ARQUIVO_BRUTO + armazenamento de objetos) via
 * RecebimentoArquivoService.
 */
@Slf4j
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
        if (arquivos == null || arquivos.isEmpty()) {
            throw new RequisicaoInvalidaException("Envie pelo menos um arquivo.");
        }

        ConjuntoDados conjunto = origemService.obterConjuntoParaUpload(conjuntoId);
        Long usuarioId = conjunto.getUsuario() != null ? conjunto.getUsuario().getId() : null;
        Long fonteId = conjunto.getFonte() != null ? conjunto.getFonte().getId() : null;

        List<ArquivoValidacaoResponse> resultados = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            resultados.add(receberUm(arquivo, conjuntoId, usuarioId, fonteId, conjunto.getId()));
        }
        return resultados;
    }

    private ArquivoValidacaoResponse receberUm(MultipartFile arquivo, Long conjuntoId,
                                               Long usuarioId, Long fonteId, Long conjuntoRespostaId) {
        if (arquivo == null || arquivo.isEmpty()) {
            String nome = arquivo != null ? arquivo.getOriginalFilename() : null;
            log.warn("Upload recusado: '{}' chegou vazio.", nome);
            return ArquivoValidacaoResponse.recusado(nome, null, null, fonteId, conjuntoRespostaId,
                    SituacaoEnvio.ARQUIVO_VAZIO,
                    "O arquivo chegou sem conteudo. Confira se ele nao esta corrompido e envie de novo.");
        }

        String nome = arquivo.getOriginalFilename();
        String extensao = identificador.extrairExtensao(arquivo);
        String mime = identificador.detectarMimeType(arquivo);

        if (!identificador.isFormatoAceito(arquivo)) {
            String motivo = "Formato nao aceito (extensao '" + extensao + "', tipo detectado '" + mime
                    + "'). Aceitos: .zip com shapefile, .geojson, .gpkg, .csv e .tif/.tiff.";
            log.warn("Upload recusado por formato: arquivo='{}'. {}", nome, motivo);
            return ArquivoValidacaoResponse.recusado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, SituacaoEnvio.FORMATO_RECUSADO, motivo);
        }

        try (InputStream in = arquivo.getInputStream()) {
            ArquivoBruto registro = recebimentoService.receber(conjuntoId, usuarioId, nome, in);
            return ArquivoValidacaoResponse.aceito(nome, extensao, mime, registro.getId(),
                    fonteId, conjuntoRespostaId);

        } catch (ArquivoDuplicadoException e) {
            // Nao e erro: o conteudo ja esta no acervo nesta competencia. Quem enviou nao
            // precisa fazer nada, e por isso DUPLICADO e um desfecho proprio.
            String motivo = e.getArquivoExistenteId() != null
                    ? "Este conteudo ja esta no acervo nesta competencia (arquivo "
                    + e.getArquivoExistenteId() + "). Nao foi preciso receber de novo."
                    : "Este conteudo ja esta no acervo nesta competencia. Nao foi preciso receber de novo.";
            log.info("Upload de '{}' dispensado por duplicidade. {}", nome, motivo);
            return ArquivoValidacaoResponse.duplicado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, e.getArquivoExistenteId(), motivo);

        } catch (RequisicaoInvalidaException | RecursoNaoEncontradoException | ConflitoException e) {
            // regra de cadastro: conjunto incompativel, responsavel inexistente, nome invalido
            log.warn("Upload de '{}' recusado por regra: {}", nome, e.getMessage());
            return ArquivoValidacaoResponse.recusado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, SituacaoEnvio.RECUSADO, e.getMessage());

        } catch (IOException | RuntimeException e) {
            // inesperado: leitura do multipart, gravacao na OCI, banco. Vale tentar de novo.
            log.error("Falha ao receber '{}' na zona bruta: {}: {}",
                    nome, e.getClass().getSimpleName(), e.getMessage(), e);
            return ArquivoValidacaoResponse.recusado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, SituacaoEnvio.ERRO,
                    "Falha ao gravar na zona bruta: " + mensagemDe(e) + ". Tente enviar de novo.");
        }
    }

    private static String mensagemDe(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
