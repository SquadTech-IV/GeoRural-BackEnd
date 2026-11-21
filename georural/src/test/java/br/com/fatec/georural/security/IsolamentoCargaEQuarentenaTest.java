package br.com.fatec.georural.security;

import br.com.fatec.georural.controller.ConsultaExecucaoQuarentenaController;
import br.com.fatec.georural.entity.Auditoria;
import br.com.fatec.georural.entity.Quarentena;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.GlobalExceptionHandler;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.QuarentenaRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.service.auditoria.AuditoriaService;
import br.com.fatec.georural.service.qualidade.ConsultaExecucaoQuarentenaService;
import br.com.fatec.georural.service.qualidade.QuarentenaSituacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IsolamentoCargaEQuarentenaTest {

    @Mock
    private UsuarioAutenticadoProvider usuarioProvider;

    @Mock
    private QuarentenaRepository quarentenaRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private IsolamentoCargaService isolamentoService;
    private QuarentenaSituacaoService quarentenaSituacaoService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        isolamentoService = new IsolamentoCargaService(usuarioProvider, quarentenaRepository, jdbcTemplate);
        quarentenaSituacaoService = new QuarentenaSituacaoService(
                quarentenaRepository,
                auditoriaService,
                isolamentoService
        );

        var controller = new ConsultaExecucaoQuarentenaController(
                mock(ConsultaExecucaoQuarentenaService.class),
                quarentenaSituacaoService
        );
        JsonMapper mapper = JsonMapper.builder()
        .findAndAddModules()
        .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
                .build();
    }

    @Test
    @DisplayName("OPERADOR nao pode reprocessar arquivo raiz de outro operador (403 AccessDeniedException)")
    void operador_deveSerBloqueadoEmArquivoDeOutroOperador() {
        when(usuarioProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(3L, "sub-op1", "S", "OPERADOR")));
        when(quarentenaRepository.findDonoArquivoRaizIdPorArquivo(50L)).thenReturn(6L);

        assertThrows(AccessDeniedException.class, () -> isolamentoService.validarAcessoArquivoBruto(50L));
    }

    @Test
    @DisplayName("OPERADOR pode reprocessar arquivo derivado de sua propria carga raiz")
    void operador_deveTerAcessoAoProprioArquivoRaiz() {
        when(usuarioProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(3L, "sub-op1", "S", "OPERADOR")));
        when(quarentenaRepository.findDonoArquivoRaizIdPorArquivo(50L)).thenReturn(3L);

        assertDoesNotThrow(() -> isolamentoService.validarAcessoArquivoBruto(50L));
    }

    @Test
    @DisplayName("GESTOR e ADMIN podem operar arquivo de qualquer operador")
    void gestorEAdmin_podemOperarQualquerArquivo() {
        when(usuarioProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(2L, "sub-gestor", "S", "GESTOR")));

        assertDoesNotThrow(() -> isolamentoService.validarAcessoArquivoBruto(50L));
        verifyNoInteractions(quarentenaRepository);
    }

    @Test
    @DisplayName("OPERADOR altera qua_situacao de PENDENTE para CORRIGIDO na propria carga, preenchendo qua_data_resolucao e auditoria")
    void operador_alteraQuarentenaPropria_gravaDataResolucaoEAuditoria() {
        when(usuarioProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(3L, "sub-op1", "S", "OPERADOR")));
        when(quarentenaRepository.findDonoArquivoRaizIdPorQuarentena(100L)).thenReturn(3L);

        Usuario op1 = new Usuario();
        op1.setId(3L);
        op1.setNome("Operador Um");
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(op1));

        Quarentena q = new Quarentena();
        q.setId(100L);
        q.setSituacao("PENDENTE");
        q.setMotivoErro("Geometria invalida");
        q.setDataRegistro(LocalDateTime.now().minusHours(1));
        when(quarentenaRepository.findById(100L)).thenReturn(Optional.of(q));
        when(quarentenaRepository.save(any(Quarentena.class))).thenAnswer(inv -> inv.getArgument(0));

        var resposta = quarentenaSituacaoService.alterarSituacao(100L, "CORRIGIDO");

        assertEquals("CORRIGIDO", resposta.getSituacao());
        assertNotNull(q.getDataResolucao());

        ArgumentCaptor<Auditoria> audCaptor = ArgumentCaptor.forClass(Auditoria.class);

        ArgumentCaptor<String> dadosAntigosCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> dadosNovosCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrar(
                eq("ATUALIZACAO_SITUACAO"),
                eq("QUARENTENA"),
                eq(100L),
                dadosAntigosCaptor.capture(),
                dadosNovosCaptor.capture(),
                any(LocalDateTime.class)
        );
        Auditoria aud = audCaptor.getValue();
        assertEquals("ATUALIZACAO_SITUACAO", aud.getOperacao());
        assertEquals("QUARENTENA", aud.getEntidadeAfetada());
        assertEquals(100L, aud.getRegistroId());
        assertTrue(dadosAntigosCaptor.getValue().contains("PENDENTE"));
        assertTrue(dadosNovosCaptor.getValue().contains("CORRIGIDO"));
    }

    @Test
    @DisplayName("OPERADOR nao pode alterar qua_situacao de registro pertencente a outro operador (403)")
    void operador_bloqueadoAoAlterarQuarentenaDeOutroOperador() {
        when(usuarioProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(3L, "sub-op1", "S", "OPERADOR")));
        when(quarentenaRepository.findDonoArquivoRaizIdPorQuarentena(100L)).thenReturn(6L);

        Quarentena q = new Quarentena();
        q.setId(100L);
        q.setSituacao("PENDENTE");
        when(quarentenaRepository.findById(100L)).thenReturn(Optional.of(q));

        assertThrows(AccessDeniedException.class, () -> quarentenaSituacaoService.alterarSituacao(100L, "DESCARTADO"));
        verify(quarentenaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Transicao final e irreversivel: nao permite alterar registro que ja esta CORRIGIDO ou DESCARTADO")
    void transicaoFinal_naoPermiteNovaAlteracao() {
        when(usuarioProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(2L, "sub-gestor", "S", "GESTOR")));

        Quarentena q = new Quarentena();
        q.setId(100L);
        q.setSituacao("DESCARTADO");
        when(quarentenaRepository.findById(100L)).thenReturn(Optional.of(q));

        assertThrows(RequisicaoInvalidaException.class, () -> quarentenaSituacaoService.alterarSituacao(100L, "CORRIGIDO"));
    }

    @Test
    @DisplayName("PATCH /api/v1/qualidade/quarentena/{id}/situacao rejeita qualquer outro campo (ex: qua_dado_original) com 400")
    void endpointQuarentena_rejeitaCamposExtrasCom400() throws Exception {
        mockMvc.perform(patch("/api/v1/qualidade/quarentena/100/situacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qua_situacao\":\"CORRIGIDO\",\"qua_dado_original\":\"alterado\"}"))
                .andExpect(status().isBadRequest());
    }
}