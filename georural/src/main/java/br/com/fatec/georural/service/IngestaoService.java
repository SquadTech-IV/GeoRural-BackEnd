package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoValidacaoResponse;
import br.com.fatec.georural.dto.response.ingestao.EnvioLoteResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.ArquivoDuplicadoException;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.FormatoInvalidoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.service.ingestao.SituacaoEnvio;
import br.com.fatec.georural.service.pipeline.ResolvedorCompetencia;
import br.com.fatec.georural.service.storage.RecebimentoArquivoService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

// upload manual de um ou varios arquivos direto na zona BRUTA do data lake
@Slf4j
@Service
public class IngestaoService {

    private static final Pattern FORMA_CANONICA =
            Pattern.compile("(19|20)\\d{2}(-(0[1-9]|1[0-2]))?");

    private final IdentificadorFormatoService identificador;
    private final OrigemArquivoService origemService;
    private final RecebimentoArquivoService recebimentoService;
    private final ResolvedorCompetencia resolvedorCompetencia;

    // teto no NUMERO de arquivos, nao no tamanho: arquivo grande continua passando.
    // zero ou negativo desliga o teto
    @Value("${georural.ingestao.max-arquivos-por-envio:200}")
    private int maxArquivosPorEnvio;

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

    // versao antiga, de dois argumentos
    public List<ArquivoValidacaoResponse> ingerir(List<MultipartFile> arquivos, Long conjuntoId) {
        return ingerir(arquivos, conjuntoId, null, null);
    }

    // competencia vale para todos os arquivos da chamada; nula, cada um deduz do nome.
    // usuId nulo usa o responsavel do conjunto.
    public List<ArquivoValidacaoResponse> ingerir(List<MultipartFile> arquivos, Long conjuntoId,
                                                  String competencia, Long usuId) {
        Preparo preparo = preparar(arquivos, conjuntoId, competencia, usuId);

        List<ArquivoValidacaoResponse> resultados = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            resultados.add(receberUm(arquivo, conjuntoId, preparo.usuarioId(), preparo.fonteId(),
                    preparo.conjuntoRespostaId(), preparo.competencia()));
        }
        return resultados;
    }

    // mesmo envio da ingerir, com totais e um id de correlacao para a tela e para o log
    public EnvioLoteResponse ingerirLote(List<MultipartFile> arquivos, Long conjuntoId,
                                         String competencia, Long usuId) {
        String envioId = UUID.randomUUID().toString();
        LocalDateTime inicio = LocalDateTime.now();

        Preparo preparo = preparar(arquivos, conjuntoId, competencia, usuId);

        List<ArquivoValidacaoResponse> itens = new ArrayList<>();
        for (MultipartFile arquivo : arquivos) {
            itens.add(receberUm(arquivo, conjuntoId, preparo.usuarioId(), preparo.fonteId(),
                    preparo.conjuntoRespostaId(), preparo.competencia()));
        }

        // o envio so tem uma competencia quando todos os arquivos ficaram na mesma
        List<String> competencias = itens.stream()
                .map(ArquivoValidacaoResponse::competencia)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();
        String competenciaUsada = competencias.size() == 1 ? competencias.get(0) : null;

        EnvioLoteResponse resposta = EnvioLoteResponse.de(envioId, inicio, conjuntoId,
                preparo.usuarioId(), competenciaUsada, itens);

        log.info("Envio {} no conjunto {}: {} enviado(s), {} aceito(s), {} duplicado(s), "
                        + "{} recusado(s), {} byte(s) em {}s.",
                envioId, conjuntoId, resposta.totais().enviados(), resposta.totais().aceitos(),
                resposta.totais().duplicados(), resposta.totais().recusados(),
                resposta.totais().bytesEnviados(), resposta.duracaoSegundos());
        return resposta;
    }

    // o que vale para o lote inteiro: lista, conjunto, responsavel e competencia
    private Preparo preparar(List<MultipartFile> arquivos, Long conjuntoId,
                             String competencia, Long usuId) {
        if (arquivos == null || arquivos.isEmpty()) {
            throw new RequisicaoInvalidaException("Envie pelo menos um arquivo.");
        }
        if (maxArquivosPorEnvio > 0 && arquivos.size() > maxArquivosPorEnvio) {
            throw new RequisicaoInvalidaException("Envio com " + arquivos.size()
                    + " arquivos; o maximo por chamada e " + maxArquivosPorEnvio
                    + ". Divida em envios menores.");
        }

        // o conjunto vale para o lote inteiro: nao servindo, a requisicao toda e recusada
        ConjuntoDados conjunto = origemService.obterConjuntoParaUpload(conjuntoId);
        Long fonteId = conjunto.getFonte() != null ? conjunto.getFonte().getId() : null;

        // informado na chamada ganha do cadastro do conjunto
        Long usuarioId = usuId != null
                ? usuId
                : (conjunto.getUsuario() != null ? conjunto.getUsuario().getId() : null);

        // normaliza uma vez: "11/2026" e "2026-11" sao a mesma competencia
        String competenciaInformada = informada(competencia)
                ? resolvedorCompetencia.resolver(competencia, null)
                : null;
        if (competenciaInformada != null) {
            // valor fora do padrao entra (a coluna e texto livre), mas fica o aviso
            if (!FORMA_CANONICA.matcher(competenciaInformada).matches()) {
                log.warn("Competencia informada '{}' nao tem a forma yyyy nem yyyy-MM. Ela vai "
                                + "para o banco assim mesmo, mas confira se nao foi erro de digitacao: "
                                + "competencia errada separa o acervo em dois periodos caladamente.",
                        competenciaInformada);
            }
            log.info("Envio no conjunto {}: {} arquivo(s) na competencia {}.",
                    conjuntoId, arquivos.size(), competenciaInformada);
        } else {
            log.info("Envio no conjunto {}: {} arquivo(s) sem competencia informada; "
                    + "cada um tera a sua deduzida do nome.", conjuntoId, arquivos.size());
        }

        return new Preparo(usuarioId, fonteId, conjunto.getId(), competenciaInformada);
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
                    competencia, 0L);
        }

        String nome = arquivo.getOriginalFilename();
        String extensao = identificador.extrairExtensao(arquivo);
        String mime = identificador.detectarMimeType(arquivo);
        long tamanho = arquivo.getSize();

        if (!identificador.isFormatoAceito(arquivo)) {
            String motivo = "Formato nao aceito (extensao '" + extensao + "', tipo detectado '" + mime
                    + "'). Aceitos: .zip com shapefile, .geojson, .gpkg, .csv e .tif/.tiff.";
            log.warn("Upload recusado por formato: arquivo='{}'. {}", nome, motivo);
            return ArquivoValidacaoResponse.recusado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, SituacaoEnvio.FORMATO_RECUSADO, motivo, competencia, tamanho);
        }

        try (InputStream in = arquivo.getInputStream()) {
            ArquivoBruto registro =
                    recebimentoService.receber(conjuntoId, usuarioId, nome, in, competencia);
            // competencia lida do registro: e a que ficou gravada
            return ArquivoValidacaoResponse.aceito(nome, extensao, mime, registro.getId(),
                    fonteId, conjuntoRespostaId, registro.getCompetencia(), tamanho);

        } catch (ArquivoDuplicadoException e) {
            // conteudo ja no acervo nesta competencia: desfecho proprio, nao e falha
            String motivo = e.getArquivoExistenteId() != null
                    ? "Este conteudo ja esta no acervo nesta competencia (arquivo "
                    + e.getArquivoExistenteId() + "). Nao foi preciso receber de novo."
                    : "Este conteudo ja esta no acervo nesta competencia. Nao foi preciso receber de novo.";
            log.info("Upload de '{}' dispensado por duplicidade. {}", nome, motivo);
            return ArquivoValidacaoResponse.duplicado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, e.getArquivoExistenteId(), motivo, competencia, tamanho);

        } catch (FormatoInvalidoException e) {
            // extensao ou assinatura de conteudo: o detector de MIME passou, o validador nao
            log.warn("Upload de '{}' recusado por formato no conteudo: {}", nome, e.getMessage());
            return ArquivoValidacaoResponse.recusado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, SituacaoEnvio.FORMATO_RECUSADO, e.getMessage(),
                    competencia, tamanho);

        } catch (RequisicaoInvalidaException | RecursoNaoEncontradoException | ConflitoException e) {
            // regra de cadastro: conjunto incompativel, responsavel inexistente, nome invalido
            log.warn("Upload de '{}' recusado por regra: {}", nome, e.getMessage());
            return ArquivoValidacaoResponse.recusado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, SituacaoEnvio.RECUSADO, e.getMessage(), competencia, tamanho);

        } catch (IOException | RuntimeException e) {
            // inesperado: multipart, OCI ou banco
            log.error("Falha ao receber '{}' na zona bruta: {}: {}",
                    nome, e.getClass().getSimpleName(), e.getMessage(), e);
            return ArquivoValidacaoResponse.recusado(nome, extensao, mime, fonteId,
                    conjuntoRespostaId, SituacaoEnvio.ERRO,
                    "Falha ao gravar na zona bruta: " + mensagemDe(e) + ". Tente enviar de novo.",
                    competencia, tamanho);
        }
    }

    // dados do lote resolvidos uma vez
    private record Preparo(Long usuarioId, Long fonteId, Long conjuntoRespostaId, String competencia) {
    }

    private static boolean informada(String competencia) {
        return competencia != null && !competencia.isBlank();
    }

    private static String mensagemDe(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
