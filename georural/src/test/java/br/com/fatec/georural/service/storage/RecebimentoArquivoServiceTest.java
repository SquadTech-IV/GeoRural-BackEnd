package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.ArquivoDuplicadoException;
import br.com.fatec.georural.exception.ObjetoJaExisteException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import com.oracle.bmc.model.BmcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do fluxo de recebimento. Repositórios e armazenamento são simulados.
 *
 * <p>Não provam: a unicidade do hash no banco (constraint uk_arquivo_hash), a permissão de apagar na IAM
 * da OCI nem a regra de retenção do bucket. Esses pontos dependem do Oracle e da OCI reais.
 */
class RecebimentoArquivoServiceTest {

    private static final Long CONJUNTO_ID = 1L;
    private static final Long USUARIO_ID = 2L;
    private static final byte[] CONTEUDO = "abc".getBytes(StandardCharsets.UTF_8);
    private static final String HASH_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private UsuarioRepository usuarioRepository;
    private ArquivoBrutoRepository arquivoRepository;
    private VerificadorDuplicidade verificadorDuplicidade;
    private ArmazenamentoZonaPort armazenamento;
    private ValidadorConjuntoDados validadorConjuntoDados;
    private RecebimentoArquivoService service;

    private ConjuntoDados conjunto;

    // Registros do que aconteceu durante as chamadas simuladas.
    private final List<String> situacoesNoFlush = new ArrayList<>();
    private final AtomicReference<byte[]> bytesGravados = new AtomicReference<>();
    private final AtomicReference<String> caminhoGravado = new AtomicReference<>();
    private final AtomicReference<Long> tamanhoGravado = new AtomicReference<>();
    private final AtomicReference<Map<String, String>> metadadosGravados = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        usuarioRepository = Mockito.mock(UsuarioRepository.class);
        arquivoRepository = Mockito.mock(ArquivoBrutoRepository.class);
        verificadorDuplicidade = Mockito.mock(VerificadorDuplicidade.class);
        armazenamento = Mockito.mock(ArmazenamentoZonaPort.class);
        validadorConjuntoDados = Mockito.mock(ValidadorConjuntoDados.class);

        service = new RecebimentoArquivoService(
                usuarioRepository,
                arquivoRepository,
                verificadorDuplicidade,
                armazenamento,
                validadorConjuntoDados,
                new ValidadorFormatoArquivo());

        conjunto = new ConjuntoDados();
        conjunto.setId(CONJUNTO_ID);
        conjunto.setSituacao("PUBLICADO");
        conjunto.setFormaEntrada("UPLOAD");

        when(validadorConjuntoDados.validar(CONJUNTO_ID)).thenReturn(conjunto);
        when(usuarioRepository.existsById(USUARIO_ID)).thenReturn(true);

        // saveAndFlush registra a situação no momento da chamada (o mesmo objeto é alterado depois).
        when(arquivoRepository.saveAndFlush(any(ArquivoBruto.class))).thenAnswer(invocacao -> {
            ArquivoBruto registro = invocacao.getArgument(0);
            situacoesNoFlush.add(registro.getStatus());
            return registro;
        });
        when(arquivoRepository.save(any(ArquivoBruto.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        // A gravação lê o stream recebido durante a chamada, porque ele é fechado em seguida.
        doAnswer(invocacao -> {
            caminhoGravado.set(invocacao.getArgument(1));
            InputStream entrada = invocacao.getArgument(2);
            bytesGravados.set(entrada.readAllBytes());
            tamanhoGravado.set(invocacao.getArgument(3));
            metadadosGravados.set(invocacao.getArgument(4));
            return null;
        }).when(armazenamento).gravar(eq(Zona.BRUTA), any(), any(), anyLong(), any());
    }

    private ArquivoBruto receberCsv() {
        return receber("dados.csv", CONTEUDO);
    }

    private ArquivoBruto receber(String nome, byte[] conteudo) {
        return service.receber(CONJUNTO_ID, USUARIO_ID, nome, new ByteArrayInputStream(conteudo));
    }

    private static BmcException bmc(int status) {
        return new BmcException(status, "CodigoDeTeste", "falha simulada", "opc-teste");
    }

    private static long contarTemporarios() throws IOException {
        try (Stream<Path> arquivos = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return arquivos.filter(p -> p.getFileName().toString().startsWith("georural-recebimento-")).count();
        }
    }

    private ArquivoBruto registroAbandonado() {
        ArquivoBruto registro = new ArquivoBruto();
        registro.setId(10L);
        registro.setConjunto(conjunto);
        registro.setStatus("RECEBENDO");
        registro.setHash(HASH_ABC);
        registro.setFormato("CSV");
        registro.setNome("dados.csv");
        registro.setPathArmazenamento("conjuntos/1/brutos/2026/10/" + HASH_ABC + "_dados.csv");
        registro.setDataUpload(LocalDateTime.now().minusHours(1));
        return registro;
    }

    // ------------------------------------------------------------------ sucesso

    @Test
    void receber_arquivoValido_deveGravarSemAlterarERegistrarComoRecebido() throws IOException {
        long temporariosAntes = contarTemporarios();

        ArquivoBruto resultado = receberCsv();

        assertEquals("RECEBIDO", resultado.getStatus());
        assertEquals(HASH_ABC, resultado.getHash());
        assertEquals("SHA-256", resultado.getAlgoritmoHash());
        assertEquals("UPLOAD", resultado.getCanalEntrada());
        assertEquals("CSV", resultado.getFormato());
        assertEquals("dados.csv", resultado.getNome());
        assertEquals(3L, resultado.getTamanhoBytes());
        assertSame(conjunto, resultado.getConjunto());

        // Conteúdo idêntico ao recebido, e caminho e metadados coerentes com o registro.
        assertArrayEquals(CONTEUDO, bytesGravados.get());
        assertEquals(3L, tamanhoGravado.get());
        assertEquals(resultado.getPathArmazenamento(), caminhoGravado.get());
        assertTrue(caminhoGravado.get().contains(HASH_ABC));
        assertEquals(HASH_ABC, metadadosGravados.get().get("hash-sha256"));
        assertEquals("1", metadadosGravados.get().get("con-id"));
        assertEquals("2", metadadosGravados.get().get("usu-id"));

        // A vaga é reservada (RECEBENDO) antes da gravação; a conclusão vem depois.
        assertEquals(List.of("RECEBENDO"), situacoesNoFlush);
        InOrder ordem = inOrder(arquivoRepository, armazenamento);
        ordem.verify(arquivoRepository).saveAndFlush(any(ArquivoBruto.class));
        ordem.verify(armazenamento).gravar(eq(Zona.BRUTA), any(), any(), anyLong(), any());
        ordem.verify(arquivoRepository).save(any(ArquivoBruto.class));

        assertEquals(temporariosAntes, contarTemporarios());
    }

    @Test
    void receber_deveRegistrarOResponsavelInformado() {
        Usuario responsavel = Mockito.mock(Usuario.class);
        when(usuarioRepository.getReferenceById(USUARIO_ID)).thenReturn(responsavel);

        ArquivoBruto resultado = receberCsv();

        assertSame(responsavel, resultado.getUsuario());
    }

    // ------------------------------------------------------------ entradas ausentes

    @Test
    void receber_semConjunto_deveLancarRequisicaoInvalidaSemGravarNada() {
        assertThrows(RequisicaoInvalidaException.class,
                () -> service.receber(null, USUARIO_ID, "dados.csv", new ByteArrayInputStream(CONTEUDO)));

        verifyNoInteractions(arquivoRepository, armazenamento);
    }

    @Test
    void receber_semResponsavel_deveLancarRequisicaoInvalidaSemGravarNada() {
        assertThrows(RequisicaoInvalidaException.class,
                () -> service.receber(CONJUNTO_ID, null, "dados.csv", new ByteArrayInputStream(CONTEUDO)));

        verifyNoInteractions(arquivoRepository, armazenamento);
    }

    @Test
    void receber_semConteudo_deveLancarRequisicaoInvalidaSemGravarNada() {
        assertThrows(RequisicaoInvalidaException.class,
                () -> service.receber(CONJUNTO_ID, USUARIO_ID, "dados.csv", null));

        verifyNoInteractions(arquivoRepository, armazenamento);
    }

    // ------------------------------------------------------------------- conjunto

    @Test
    void receber_conjuntoInexistente_deveLancarRecursoNaoEncontradoSemGravarNada() {
        doThrow(new RecursoNaoEncontradoException("Conjunto de dados não encontrado: 1"))
                .when(validadorConjuntoDados).validar(CONJUNTO_ID);

        assertThrows(RecursoNaoEncontradoException.class, () -> receberCsv());

        verifyNoInteractions(arquivoRepository, armazenamento);
    }

    @Test
    void receber_conjuntoNaoElegivel_devePropagarARequisicaoInvalidaSemGravarNada() {
        doThrow(new RequisicaoInvalidaException("Conjunto arquivado"))
                .when(validadorConjuntoDados).validar(CONJUNTO_ID);

        assertThrows(RequisicaoInvalidaException.class, () -> receberCsv());

        verifyNoInteractions(arquivoRepository, armazenamento);
    }

    // ------------------------------------------------------------------- formato

    @Test
    void receber_extensaoNaoAceita_deveLancarRequisicaoInvalidaSemGravarNada() {
        assertThrows(RequisicaoInvalidaException.class, () -> receber("dados.json", CONTEUDO));

        verifyNoInteractions(arquivoRepository, armazenamento);
    }

    @Test
    void receber_conteudoIncompativelComAExtensao_deveLancarRequisicaoInvalidaSemReservarVaga() throws IOException {
        long temporariosAntes = contarTemporarios();

        assertThrows(RequisicaoInvalidaException.class, () -> receber("dados.gpkg", CONTEUDO));

        verifyNoInteractions(arquivoRepository, armazenamento);
        assertEquals(temporariosAntes, contarTemporarios());
    }

    // ----------------------------------------------------------------- responsável

    @Test
    void receber_responsavelInexistente_deveLancarRecursoNaoEncontradoSemGravarNada() {
        when(usuarioRepository.existsById(USUARIO_ID)).thenReturn(false);

        assertThrows(RecursoNaoEncontradoException.class, () -> receberCsv());

        verifyNoInteractions(arquivoRepository, armazenamento);
    }

    // ------------------------------------------------------------------ duplicidade

    @Test
    void receber_arquivoDuplicado_deveLancarDuplicadoSemReservarVagaNemGravar() {
        when(verificadorDuplicidade.verificar(CONJUNTO_ID, HASH_ABC))
                .thenThrow(new ArquivoDuplicadoException(CONJUNTO_ID, HASH_ABC));

        assertThrows(ArquivoDuplicadoException.class, () -> receberCsv());

        verify(arquivoRepository, never()).saveAndFlush(any(ArquivoBruto.class));
        verifyNoInteractions(armazenamento);
    }

    @Test
    void receber_duplicadoSimultaneo_aViolacaoDaUnicidadeDeveVirarDuplicado() {
        doThrow(new DataIntegrityViolationException("ORA-00001: unique constraint (ADMIN.UK_ARQUIVO_HASH) violated"))
                .when(arquivoRepository).saveAndFlush(any(ArquivoBruto.class));

        assertThrows(ArquivoDuplicadoException.class, () -> receberCsv());

        verifyNoInteractions(armazenamento);
    }

    @Test
    void receber_outraViolacaoDeIntegridade_naoDeveVirarDuplicado() {
        doThrow(new DataIntegrityViolationException("ORA-02291: integrity constraint (ADMIN.FK_ARQ_USUARIO) violated"))
                .when(arquivoRepository).saveAndFlush(any(ArquivoBruto.class));

        assertThrows(DataIntegrityViolationException.class, () -> receberCsv());

        verifyNoInteractions(armazenamento);
    }

    // ----------------------------------------------------------- falhas parciais

    @Test
    void receber_falhaNaCopiaParaOTemporario_deveLancarUncheckedIOExceptionSemRegistrarNemGravar() throws IOException {
        long temporariosAntes = contarTemporarios();
        InputStream quebrado = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("falha de leitura simulada");
            }
        };

        assertThrows(UncheckedIOException.class,
                () -> service.receber(CONJUNTO_ID, USUARIO_ID, "dados.csv", quebrado));

        verifyNoInteractions(arquivoRepository, armazenamento);
        assertEquals(temporariosAntes, contarTemporarios());
    }

    @Test
    void receber_falhaAoReservarAVaga_naoDeveGravarNaZonaBruta() {
        doThrow(new RuntimeException("banco indisponível"))
                .when(arquivoRepository).saveAndFlush(any(ArquivoBruto.class));

        assertThrows(RuntimeException.class, () -> receberCsv());

        verifyNoInteractions(armazenamento);
    }

    @Test
    void receber_falhaNaPromocaoComObjetoInexistente_deveDescartarORegistro() throws IOException {
        long temporariosAntes = contarTemporarios();
        doThrow(bmc(500)).when(armazenamento).gravar(eq(Zona.BRUTA), any(), any(), anyLong(), any());
        when(armazenamento.existe(eq(Zona.BRUTA), any())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> receberCsv());

        verify(arquivoRepository).delete(any(ArquivoBruto.class));
        verify(arquivoRepository, never()).save(any(ArquivoBruto.class));
        assertEquals(temporariosAntes, contarTemporarios());
    }

    @Test
    void receber_falhaNaPromocaoComObjetoExistente_deveConcluirComoRecebido() {
        doThrow(bmc(500)).when(armazenamento).gravar(eq(Zona.BRUTA), any(), any(), anyLong(), any());
        when(armazenamento.existe(eq(Zona.BRUTA), any())).thenReturn(true);

        ArquivoBruto resultado = receberCsv();

        assertEquals("RECEBIDO", resultado.getStatus());
        verify(arquivoRepository, never()).delete(any(ArquivoBruto.class));
    }

    @Test
    void receber_falhaNaPromocaoSemPoderConfirmarOObjeto_deveManterORegistroRecebendo() {
        doThrow(bmc(500)).when(armazenamento).gravar(eq(Zona.BRUTA), any(), any(), anyLong(), any());
        when(armazenamento.existe(eq(Zona.BRUTA), any())).thenThrow(bmc(503));

        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> receberCsv());

        assertTrue(erro.getMessage().contains("RECEBENDO"));
        verify(arquivoRepository, never()).delete(any(ArquivoBruto.class));
        verify(arquivoRepository, never()).save(any(ArquivoBruto.class));
    }

    @Test
    void receber_promocaoRejeitadaPorObjetoJaExistente_deveConcluirSemConsultarExistencia() {
        doThrow(new ObjetoJaExisteException("conjuntos/1/a.csv")).when(armazenamento).gravar(eq(Zona.BRUTA), any(), any(), anyLong(), any());

        ArquivoBruto resultado = receberCsv();

        assertEquals("RECEBIDO", resultado.getStatus());
        verify(armazenamento, never()).existe(eq(Zona.BRUTA), any());
        verify(arquivoRepository, never()).delete(any(ArquivoBruto.class));
    }

    @Test
    void receber_falhaAoConcluirORegistro_naoDeveDescartarOregistroDeObjetoJaGravado() {
        doThrow(new RuntimeException("banco indisponível"))
                .when(arquivoRepository).save(any(ArquivoBruto.class));

        assertThrows(RuntimeException.class, () -> receberCsv());

        verify(armazenamento, times(1)).gravar(eq(Zona.BRUTA), any(), any(), anyLong(), any());
        verify(arquivoRepository, never()).delete(any(ArquivoBruto.class));
    }

    // -------------------------------------------------------------------- retomada

    @Test
    void receber_registroAbandonadoComObjetoJaGravado_deveConcluirSemGravarDeNovo() {
        ArquivoBruto abandonado = registroAbandonado();
        when(verificadorDuplicidade.verificar(CONJUNTO_ID, HASH_ABC)).thenReturn(Optional.of(abandonado));
        when(armazenamento.existe(Zona.BRUTA, abandonado.getPathArmazenamento())).thenReturn(true);

        ArquivoBruto resultado = receberCsv();

        assertSame(abandonado, resultado);
        assertEquals("RECEBIDO", resultado.getStatus());
        verify(armazenamento, never()).gravar(eq(Zona.BRUTA), any(), any(), anyLong(), any());
        verify(arquivoRepository, never()).saveAndFlush(any(ArquivoBruto.class));
    }

    @Test
    void receber_registroAbandonadoSemObjeto_deveGravarNoCaminhoJaRegistradoSemCriarOutroRegistro() {
        ArquivoBruto abandonado = registroAbandonado();
        String caminhoOriginal = abandonado.getPathArmazenamento();
        when(verificadorDuplicidade.verificar(CONJUNTO_ID, HASH_ABC)).thenReturn(Optional.of(abandonado));
        when(armazenamento.existe(Zona.BRUTA, caminhoOriginal)).thenReturn(false);

        ArquivoBruto resultado = receberCsv();

        assertEquals(caminhoOriginal, caminhoGravado.get());
        assertEquals(caminhoOriginal, resultado.getPathArmazenamento());
        assertArrayEquals(CONTEUDO, bytesGravados.get());
        assertEquals("RECEBIDO", resultado.getStatus());
        verify(arquivoRepository, never()).saveAndFlush(any(ArquivoBruto.class));
        assertTrue(situacoesNoFlush.isEmpty());
    }

    // ------------------------------------------------------------------ não confundir

    @Test
    void receber_comNomeComCaminho_deveRegistrarSomenteONomeDoArquivo() {
        ArquivoBruto resultado = receber("C:\\temp\\pasta\\dados.csv", CONTEUDO);

        assertEquals("dados.csv", resultado.getNome());
        assertTrue(resultado.getPathArmazenamento().endsWith("_dados.csv"));
        assertFalse(resultado.getPathArmazenamento().contains(".."));
        verify(arquivoRepository, times(1)).saveAndFlush(any(ArquivoBruto.class));
        verify(armazenamento, times(1)).gravar(eq(Zona.BRUTA), eq(resultado.getPathArmazenamento()), any(), eq(3L), any());
    }
}