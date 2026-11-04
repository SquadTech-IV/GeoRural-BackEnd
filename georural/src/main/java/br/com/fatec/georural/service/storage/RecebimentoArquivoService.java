package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.ArquivoDuplicadoException;
import br.com.fatec.georural.exception.ObjetoJaExisteException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.service.pipeline.ResolvedorCompetencia;
import br.com.fatec.georural.service.pipeline.formato.DetectorFormato;
import br.com.fatec.georural.service.pipeline.formato.FormatoDetectado;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class RecebimentoArquivoService {

    private static final String CANAL_UPLOAD = "UPLOAD";

    private final UsuarioRepository usuarioRepository;
    private final ArquivoBrutoRepository arquivoRepository;
    private final VerificadorDuplicidade verificadorDuplicidade;
    private final ArmazenamentoZonaPort armazenamento;
    private final ValidadorConjuntoDados validadorConjuntoDados;
    private final ValidadorFormatoArquivo validadorFormatoArquivo;
    private final ResolvedorCompetencia resolvedorCompetencia;
    private final DetectorFormato detectorFormato;

    public ArquivoBruto receber(Long conjuntoId, Long usuarioId, String nomeOriginal, InputStream conteudo) {
        return receber(conjuntoId, usuarioId, nomeOriginal, conteudo, null);
    }

    public ArquivoBruto receber(Long conjuntoId, Long usuarioId, String nomeOriginal,
                                InputStream conteudo, String competencia) {
        if (conjuntoId == null) {
            throw new RequisicaoInvalidaException("Identificador do conjunto é obrigatório.");
        }

        if (conteudo == null) {
            throw new RequisicaoInvalidaException("Conteúdo do arquivo é obrigatório.");
        }
        String nome;
        try {
            nome = CaminhoZonaBrutaHelper.extrairNomeArquivo(nomeOriginal);
        } catch (IllegalArgumentException e) {
            throw new RequisicaoInvalidaException(e.getMessage());
        }

        // 1. Validar conjunto e extensão antes de criar o arquivo temporário
        ConjuntoDados conjunto = validadorConjuntoDados.validar(conjuntoId);
        String formato = validadorFormatoArquivo.validarExtensao(nome);
        if (usuarioId != null) {
            validarResponsavel(usuarioId);
        }

        Path temporario = null;
        try {
            temporario = Files.createTempFile("georural-recebimento-", ".tmp");

            // Hash do que chegou, calculado durante a cópia; depois, hash do que foi gravado em disco.
            String hashRecebido = copiarParaTemporario(conteudo, temporario);
            long tamanho = Files.size(temporario);
            String hash = SHA256StreamCalculator.calcular(temporario);
            if (!hash.equals(hashRecebido)) {
                throw new IllegalStateException(
                        "O arquivo temporário não confere com o conteúdo recebido (hash divergente).");
            }

            // 2. Validar o conteúdo (assinatura dos primeiros bytes) depois da cópia e do hash, e antes de duplicidade
            validadorFormatoArquivo.validarConteudo(temporario, formato);

            String competenciaResolvida = resolvedorCompetencia.resolver(competencia, nome);

            // A duplicidade agora e avaliada pela identidade correta:
            Optional<ArquivoBruto> abandonado =
                    verificadorDuplicidade.verificar(conjuntoId, competenciaResolvida, hash);
            boolean retomada = abandonado.isPresent();
            ArquivoBruto registro = abandonado.orElseGet(
                    () -> criarRegistroRecebendo(conjunto, conjuntoId, usuarioId, nome, formato,
                                                 hash, tamanho, competenciaResolvida));

            if (registro.getFormato() == null) {
                registro.setFormato(formato);
            }

            // Formato e sistema de referencia vem do CONTEUDO
            aplicarDeteccao(registro, temporario, nome, conjunto);

            Map<String, String> metadados = montarMetadados(hash, conjuntoId, usuarioId);
            boolean jaPromovido = retomada && armazenamento.existe(Zona.BRUTA, registro.getPathArmazenamento());
            if (!jaPromovido) {
                promover(registro, temporario, tamanho, metadados);
            }

            registro.setStatus(SituacaoArquivoBruto.RECEBIDO);
            return arquivoRepository.save(registro);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao tratar o arquivo na área temporária.", e);
        } finally {
            limparTemporario(temporario);
        }
    }

    private void validarResponsavel(Long usuarioId) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new RecursoNaoEncontradoException("Usuário não encontrado: " + usuarioId);
        }
    }


    private void aplicarDeteccao(ArquivoBruto registro, Path temporario, String nome, ConjuntoDados conjunto) {
        try {
            FormatoDetectado detectado = detectorFormato.detectar(Files.readAllBytes(temporario), nome);
            registro.setFormatoDetectado(detectado.formato().name());
            registro.setSrsDeclarado(detectado.srsEpsg());
            registro.setSrsUtilizado(detectado.srsEpsg() != null
                    ? detectado.srsEpsg()
                    : (conjunto.getSrs() != null ? conjunto.getSrs() : 4674));
            if (detectado.observacao() != null) {
                log.info("Arquivo '{}': {}", nome, detectado.observacao());
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Nao foi possivel detectar formato/SRC de '{}': {}", nome, e.getMessage());
        }
    }

    private ArquivoBruto criarRegistroRecebendo(ConjuntoDados conjunto, Long conjuntoId, Long usuarioId,
                                                String nome, String formato, String hash, long tamanho,
                                                String competencia) {
        LocalDateTime agora = LocalDateTime.now();

        ArquivoBruto registro = new ArquivoBruto();
        registro.setConjunto(conjunto);
        if (usuarioId != null) {
            registro.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        }
        registro.setNome(nome);
        registro.setFormato(formato);
        registro.setTamanhoBytes(tamanho);
        registro.setHash(hash);
        registro.setAlgoritmoHash(SHA256StreamCalculator.ALGORITMO);
        registro.setCanalEntrada(CANAL_UPLOAD);
        registro.setCompetencia(competencia);
        registro.setStatus(SituacaoArquivoBruto.RECEBENDO);
        registro.setDataUpload(agora);
        registro.setPathArmazenamento(CaminhoZonaBrutaHelper.gerarCaminho(conjuntoId, agora, hash, nome));

        try {
            return arquivoRepository.saveAndFlush(registro);
        } catch (DataIntegrityViolationException e) {
            if (violouUnicidadeConjuntoHash(e)) {
                throw new ArquivoDuplicadoException(conjuntoId, hash);
            }
            throw e;
        }
    }

    private void promover(ArquivoBruto registro, Path temporario, long tamanho, Map<String, String> metadados) {
        String caminho = registro.getPathArmazenamento();
        try (InputStream entrada = Files.newInputStream(temporario)) {
            armazenamento.gravar(Zona.BRUTA, caminho, entrada, tamanho, metadados);
        } catch (ObjetoJaExisteException e) {
            return; // o objeto já está na zona bruta: segue para concluir o registro
        } catch (IOException | RuntimeException e) {
            resolverFalhaNaPromocao(registro, caminho, e);
        }
    }

    private void resolverFalhaNaPromocao(ArquivoBruto registro, String caminho, Exception causa) {
        boolean existe;
        try {
            existe = armazenamento.existe(Zona.BRUTA, caminho);
        } catch (RuntimeException verificacao) {
            causa.addSuppressed(verificacao);
            throw new IllegalStateException(
                    "Não foi possível confirmar se o objeto foi gravado na zona bruta; "
                            + "o registro permanece RECEBENDO.", causa);
        }
        if (existe) {
            return; // gravado apesar do erro: segue para concluir o registro
        }
        descartarRegistro(registro);
        throw new IllegalStateException("Falha ao gravar o arquivo na zona bruta.", causa);
    }

    private void descartarRegistro(ArquivoBruto registro) {
        try {
            arquivoRepository.delete(registro);
        } catch (RuntimeException e) {
            // Se não for possível apagar, o registro fica RECEBENDO e será retomado depois do prazo de abandono.
            log.warn("Não foi possível descartar o registro {} após a falha; ele permanece RECEBENDO.",
                    registro.getId(), e);
        }
    }

    private static String copiarParaTemporario(InputStream conteudo, Path destino) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(SHA256StreamCalculator.ALGORITMO);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo " + SHA256StreamCalculator.ALGORITMO + " indisponível.", e);
        }
        // Files.copy não fecha o stream de entrada.
        Files.copy(new DigestInputStream(conteudo, digest), destino, StandardCopyOption.REPLACE_EXISTING);
        return HexFormat.of().formatHex(digest.digest());
    }

    // Chaves conforme a convenção documentada na T001
    private static Map<String, String> montarMetadados(String hash, Long conjuntoId, Long usuarioId) {
        return Map.of(
                "hash-sha256", hash,
                "con-id", String.valueOf(conjuntoId),
                "usu-id", String.valueOf(usuarioId));
    }

    private static boolean violouUnicidadeConjuntoHash(Throwable erro) {
        for (Throwable t = erro; t != null; t = (t.getCause() == t ? null : t.getCause())) {
            String mensagem = t.getMessage();
            if (mensagem != null && mensagem.toUpperCase().contains("UK_ARQUIVO_CON_COMP_HASH")) {
                return true;
            }
        }
        return false;
    }

    private static void limparTemporario(Path temporario) {
        if (temporario == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporario);
        } catch (IOException e) {
            log.warn("Não foi possível apagar o arquivo temporário {}.", temporario, e);
        }
    }
}