package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.ArquivoDuplicadoException;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.service.ingestao.SituacaoEnvio;
import br.com.fatec.georural.service.pipeline.ResolvedorCompetencia;
import br.com.fatec.georural.service.storage.RecebimentoArquivoService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Ingestao por upload manual, um ou varios arquivos na mesma chamada. Grava direto na
 * zona BRUTA do data lake (ARQUIVO_BRUTO + armazenamento de objetos) via
 * RecebimentoArquivoService.
 */
@Slf4j
@Service
public class IngestaoService {

    private static final Pattern FORMA_CANONICA =
            Pattern.compile("(19|20)\\d{2}(-(0[1-9]|1[0-2]))?");

    private final IdentificadorFormatoService identificador;
    private final OrigemArquivoService origemService;
    private final RecebimentoArquivoService recebimentoService;
    private final ResolvedorCompetencia resolvedorCompetencia;

    public IngestaoService(
            IdentificadorFormatoService identificador,
            OrigemArquivoService origemService,
            RecebimentoArquivoService recebimentoService,
            ResolvedorCompetencia resolvedorCompetencia
    ) {
        this.identificador = identificador;
        this.origemService = origemService;
        this.recebimentoService = recebimentoService;
        this.resolvedorCompetencia = resolvedorCompetencia;
    }

    /** Mantido para quem chamava a versao de dois argumentos. */
    public List<ArquivoValidacaoResponse> ingerir(List<MultipartFile> arquivos, Long conjuntoId) {
        return ingerir(arquivos, conjuntoId, null, null);
    }

    /**
     * @param competencia periodo a que TODOS os arquivos desta chamada se referem.
     *        Nulo ou vazio: cada arquivo tem a sua deduzida do proprio nome. Arquivos de
     *        competencias diferentes pedem chamadas diferentes -- de proposito, porque a
     *        competencia errada parte o acervo em dois sem avisar ninguem.
     * @param usuId responsavel pelo envio. Nulo: usa o responsavel do conjunto.
     */
    public List<ArquivoValidacaoResponse> ingerir(List<MultipartFile> arquivos, Long conjuntoId,
                                                  String competencia, Long usuId) {
        if (arquivos == null || arquivos.isEmpty()) {
            throw new RequisicaoInvalidaException("Envie pelo menos um arquivo.");
        }

        // o conjunto e condicao do lote inteiro, nao de um arquivo: se ele nao serve,
        // nenhum arquivo entraria, e recusar a requisicao toda e a resposta honesta
        ConjuntoDados conjunto = origemService.obterConjuntoParaUpload(conjuntoId);
        Long fonteId = conjunto.getFonte() != null ? conjunto.getFonte().getId() : null;

        // informado na chamada ganha do cadastro do conjunto
        Long usuarioId = usuId != null
                ? usuId
                : (conjunto.getUsuario() != null ? conjunto.getUsuario().getId() : null);

        // "11/2026" e "2026-11" sao a mesma competencia: normaliza uma vez, aqui, para a
        // resposta mostrar a forma que de fato vai para o banco
        String competenciaInformada = informada(competencia)
                ? resolvedorCompetencia.resolver(competencia, null)
                : null;
        if (competenciaInformada != null) {
            if (!FORMA_CANONICA.matcher(competenciaInformada).matches()) {
                log.warn("Competencia informada '{}' nao tem a forma yyyy nem yyyy-MM. Ela vai "
                                + "para o banco assim mesmo, mas confira se nao foi erro de digitacao: "
                                + "competencia errada separa o acervo em dois periodos caladamente.",
                        competenciaInformada);
            }
            log.info("Envio em lote no conjunto {}: {} arquivo(s) na competencia {}.",
                    conjuntoId, arquivos.size(), competenciaInformada);
        } else {
            log.info("Envio em lote no conjunto {}: {} arquivo(s) sem competencia informada; "
                    + "cada um tera a sua deduzida do nome.", conjuntoId, arquivos.size());
        }

        List<ArquivoValidacaoResponse> resultados = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            resultados.add(receberUm(arquivo, conjuntoId, usuarioId, fonteId,
                    conjunto.getId(), competenciaInformada));
        }
        return resultados;
    }

    private ArquivoValidacaoResponse receberUm(MultipartFile arquivo, Long conjuntoId,
                                               Long usuarioId, Long fonteId,
                                               Long conjuntoRespostaId, String competencia) {
        if (arquivo == null || arquivo.isEmpty()) {
            String nome = arquivo != null ? arquivo.getOriginalFilename() : null;
            log.warn("Upload recusado: '{}' chegou vazio.", nome);
            return ArquivoValidacaoResponse.recusado(nome, null, null, fonteId, conjuntoRespostaId,
                    SituacaoEnvio.ARQUIVO_VAZIO,
                    "O arquivo chegou sem conteudo. Confira se ele nao esta corrompido e envie de novo.",
                    competencia);
        }

        String nome = arquivo.getOriginalFilename();
        String extensao = identificador.extrairExtensao(arquivo);
        String mime = identificador.detectarMimeType(arquivo);

        if (!identificador.isFormatoAceito(arquivo)) {
            String motivo = "Formato nao aceito (extensao '" + extensao + "', tipo detectado '" + mime
                    + "'). Aceitos: .zip com shapefile, .geojson, .gpkg, .csv e .tif/.tiff.";
            log.warn("Upload recusado por formato: arquivo='{}'. {}", nome, motivo);
            return ArquivoValidacaoResponse.recusado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, SituacaoEnvio.FORMATO_RECUSADO, motivo, competencia);
        }

        try (InputStream in = arquivo.getInputStream()) {
            ArquivoBruto registro =
                    recebimentoService.receber(conjuntoId, usuarioId, nome, in, competencia);
            // a competencia da resposta vem do registro: e a que de fato ficou gravada,
            // inclusive quando foi deduzida do nome do arquivo
            return ArquivoValidacaoResponse.aceito(nome, extensao, mime, registro.getId(),
                    fonteId, conjuntoRespostaId, registro.getCompetencia());

        } catch (ArquivoDuplicadoException e) {
            // Nao e erro: o conteudo ja esta no acervo nesta competencia. Quem enviou nao
            // precisa fazer nada, e por isso DUPLICADO e um desfecho proprio.
            String motivo = e.getArquivoExistenteId() != null
                    ? "Este conteudo ja esta no acervo nesta competencia (arquivo "
                    + e.getArquivoExistenteId() + "). Nao foi preciso receber de novo."
                    : "Este conteudo ja esta no acervo nesta competencia. Nao foi preciso receber de novo.";
            log.info("Upload de '{}' dispensado por duplicidade. {}", nome, motivo);
            return ArquivoValidacaoResponse.duplicado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, e.getArquivoExistenteId(), motivo, competencia);

        } catch (RequisicaoInvalidaException | RecursoNaoEncontradoException | ConflitoException e) {
            // regra de cadastro: conjunto incompativel, responsavel inexistente, nome invalido.
            // O responsavel e conferido pelo receber() antes de qualquer leitura do arquivo,
            // entao um usuId errado recusa rapido, sem gastar o upload.
            log.warn("Upload de '{}' recusado por regra: {}", nome, e.getMessage());
            return ArquivoValidacaoResponse.recusado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, SituacaoEnvio.RECUSADO, e.getMessage(), competencia);

        } catch (IOException | RuntimeException e) {
            // inesperado: leitura do multipart, gravacao na OCI, banco. Vale tentar de novo.
            log.error("Falha ao receber '{}' na zona bruta: {}: {}",
                    nome, e.getClass().getSimpleName(), e.getMessage(), e);
            return ArquivoValidacaoResponse.recusado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, SituacaoEnvio.ERRO,
                    "Falha ao gravar na zona bruta: " + mensagemDe(e) + ". Tente enviar de novo.",
                    competencia);
        }
    }

    private static boolean informada(String competencia) {
        return competencia != null && !competencia.isBlank();
    }

    private static String mensagemDe(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
