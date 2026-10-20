package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import com.oracle.bmc.model.BmcException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import br.com.fatec.georural.exception.ArquivoDuplicadoException;
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

/**
 * Recebe um arquivo, guarda-o sem alteração na zona bruta e registra no Oracle (US#07).
 *
 * <p>Sequência: área temporária (disco local), verificação do hash, verificação de duplicidade,
 * registro RECEBENDO (reserva a vaga), promoção para a zona bruta, registro RECEBIDO.
 *
 * <p>Esta classe NÃO é {@code @Transactional}: o registro RECEBENDO precisa estar confirmado no banco
 * antes da promoção, para que o índice único reserve a vaga. Cada gravação no repositório tem a sua
 * própria transação.
 *
 * <p>A zona bruta é imutável: nada aqui apaga ou altera objeto nela. Por isso, se não for possível saber
 * se a promoção ocorreu, o registro permanece RECEBENDO (e não FALHA) até ser resolvido pela retomada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecebimentoArquivoService {

    private static final String CANAL_UPLOAD = "UPLOAD";

    private final ConjuntoDadosRepository conjuntoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ArquivoBrutoRepository arquivoRepository;
    private final VerificadorDuplicidade verificadorDuplicidade;
    private final ArmazenamentoZonaBrutaService armazenamento;

    /**
     * @param conjuntoId   conjunto de destino (a T006 acrescenta as validações de elegibilidade e de formato)
     * @param usuarioId    responsável pelo envio (a T007 acrescenta a validação contra USUARIO)
     * @param nomeOriginal nome original do arquivo
     * @param conteudo     conteúdo do arquivo; não é fechado aqui
     * @throws ArquivoDuplicadoException se já existe arquivo ativo com o mesmo hash no conjunto
     * @throws EntityNotFoundException   se o conjunto não existe
     */
    public ArquivoBruto receber(Long conjuntoId, Long usuarioId, String nomeOriginal, InputStream conteudo) {
        if (conjuntoId == null) {
            throw new IllegalArgumentException("Identificador do conjunto é obrigatório.");
        }
        if (usuarioId == null) {
            throw new IllegalArgumentException("Identificador do responsável é obrigatório.");
        }
        if (conteudo == null) {
            throw new IllegalArgumentException("Conteúdo do arquivo é obrigatório.");
        }
        String nome = CaminhoZonaBrutaHelper.extrairNomeArquivo(nomeOriginal);
        ConjuntoDados conjunto = conjuntoRepository.findById(conjuntoId)
                .orElseThrow(() -> new EntityNotFoundException("Conjunto de dados não encontrado: " + conjuntoId));

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

            Optional<ArquivoBruto> abandonado = verificadorDuplicidade.verificar(conjuntoId, hash);
            boolean retomada = abandonado.isPresent();
            ArquivoBruto registro = abandonado.orElseGet(
                    () -> criarRegistroRecebendo(conjunto, conjuntoId, usuarioId, nome, hash, tamanho));

            // Na retomada, o caminho e a data gravados no primeiro envio são reaproveitados; nunca recalculados.
            // Nesse caso o responsável gravado no objeto é o de quem reenviou, e o do registro continua o original.
            Map<String, String> metadados = montarMetadados(hash, conjuntoId, usuarioId);
            boolean jaPromovido = retomada && armazenamento.verificarExistenciaObjeto(registro.getPathArmazenamento());
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

    private ArquivoBruto criarRegistroRecebendo(ConjuntoDados conjunto, Long conjuntoId, Long usuarioId,
                                                String nome, String hash, long tamanho) {
        LocalDateTime agora = LocalDateTime.now();

        ArquivoBruto registro = new ArquivoBruto();
        registro.setConjunto(conjunto);
        registro.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        registro.setNome(nome);
        registro.setTamanhoBytes(tamanho);
        registro.setHash(hash);
        registro.setAlgoritmoHash(SHA256StreamCalculator.ALGORITMO);
        registro.setCanalEntrada(CANAL_UPLOAD);
        registro.setStatus(SituacaoArquivoBruto.RECEBENDO);
        registro.setDataUpload(agora);
        registro.setPathArmazenamento(CaminhoZonaBrutaHelper.gerarCaminho(conjuntoId, agora, hash, nome));

        try {
            // saveAndFlush confirma o registro agora (sem transação externa), reservando a vaga no índice único.
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
            armazenamento.armazenarObjetoBruto(caminho, entrada, tamanho, metadados);
        } catch (BmcException e) {
            if (PoliticaZonaBruta.ehConflitoPorObjetoJaExistente(e)) {
                return; // o objeto já está na zona bruta: segue para concluir o registro
            }
            resolverFalhaNaPromocao(registro, caminho, e);
        } catch (IOException | RuntimeException e) {
            resolverFalhaNaPromocao(registro, caminho, e);
        }
    }

    /**
     * A promoção lançou erro, mas o objeto pode ter sido gravado (por exemplo, timeout depois do envio).
     * Só marca FALHA quando é certo que o objeto não existe.
     */
    private void resolverFalhaNaPromocao(ArquivoBruto registro, String caminho, Exception causa) {
        boolean existe;
        try {
            existe = armazenamento.verificarExistenciaObjeto(caminho);
        } catch (RuntimeException verificacao) {
            causa.addSuppressed(verificacao);
            throw new IllegalStateException(
                    "Não foi possível confirmar se o objeto foi gravado na zona bruta; "
                            + "o registro permanece RECEBENDO.", causa);
        }
        if (existe) {
            return; // gravado apesar do erro: segue para concluir o registro
        }
        marcarFalha(registro);
        throw new IllegalStateException("Falha ao gravar o arquivo na zona bruta.", causa);
    }

    private void marcarFalha(ArquivoBruto registro) {
        try {
            registro.setStatus(SituacaoArquivoBruto.FALHA);
            arquivoRepository.save(registro);
        } catch (RuntimeException e) {
            // Se não for possível gravar FALHA, o registro fica RECEBENDO e será resolvido na retomada.
            log.warn("Não foi possível marcar o registro {} como FALHA.", registro.getId(), e);
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

    // Chaves conforme a convenção documentada na T001; se CaminhoZonaBrutaHelper já expõe um método
    // para montar os metadados, use-o aqui para manter a convenção em um só lugar.
    private static Map<String, String> montarMetadados(String hash, Long conjuntoId, Long usuarioId) {
        return Map.of(
                "hash-sha256", hash,
                "con-id", String.valueOf(conjuntoId),
                "usu-id", String.valueOf(usuarioId));
    }

    private static boolean violouUnicidadeConjuntoHash(Throwable erro) {
        for (Throwable t = erro; t != null; t = (t.getCause() == t ? null : t.getCause())) {
            String mensagem = t.getMessage();
            if (mensagem != null && mensagem.toUpperCase().contains("UK_ARQ_CON_HASH")) {
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