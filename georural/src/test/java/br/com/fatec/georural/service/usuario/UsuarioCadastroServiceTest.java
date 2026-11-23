package br.com.fatec.georural.service.usuario;

import br.com.fatec.georural.dto.client.keycloak.KeycloakAdminClient;
import br.com.fatec.georural.dto.client.keycloak.KeycloakUserRepresentation;
import br.com.fatec.georural.dto.request.usuario.CriarUsuarioRequest;
import br.com.fatec.georural.dto.response.usuario.UsuarioResponse;
import br.com.fatec.georural.entity.Perfil;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.KeycloakIndisponivelException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.PerfilRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.service.auditoria.AuditoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioCadastroServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PerfilRepository perfilRepository;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private UsuarioCadastroService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().doAnswer(inv -> {
            Consumer<TransactionStatus> cb = inv.getArgument(0);
            cb.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new UsuarioCadastroService(
                usuarioRepository,
                perfilRepository,
                keycloakAdminClient,
                auditoriaService,
                transactionTemplate
        );
    }

    private Perfil perfil(Long id, String funcao, String nome) {
        Perfil p = new Perfil();
        p.setId(id);
        p.setFuncao(funcao);
        p.setNome(nome);
        return p;
    }

    @Test
    @DisplayName("Deve cadastrar usuario no Keycloak e Oracle, gravar auditoria INSERT e enviar email UPDATE_PASSWORD")
    void cadastrar_sucesso_criaUsuarioAuditaEEnviaConvite() {
        Perfil operador = perfil(3L, "OPERADOR", "Operador de Carga");
        when(perfilRepository.findById(3L)).thenReturn(Optional.of(operador));
        when(usuarioRepository.existsByEmailIgnoreCase("maria@georural.gov.br")).thenReturn(false);
        when(keycloakAdminClient.criarUsuario("Maria Silva", "maria@georural.gov.br", "OPERADOR"))
                .thenReturn("sub-maria-123");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        UsuarioResponse resp = service.cadastrar(new CriarUsuarioRequest("  Maria   Silva ", " MARIA@GeoRural.gov.br ", 3L));

        assertEquals(10L, resp.id());
        assertEquals("Maria Silva", resp.nome());
        assertEquals("maria@georural.gov.br", resp.email());
        assertEquals("S", resp.status());
        assertEquals("OPERADOR", resp.perfilFuncao());
        assertEquals(Boolean.TRUE, resp.convitePendente());
        assertNull(resp.avisoEnvioEmail());

        verify(auditoriaService).registrar(eq("INSERT"), eq("USUARIO"), eq(10L), isNull(), contains("maria@georural.gov.br"));
        verify(keycloakAdminClient).enviarEmailAcao("sub-maria-123", List.of("UPDATE_PASSWORD"));
    }

    @Test
    @DisplayName("Deve lancar ConflitoException (409) quando email ja existe no banco local")
    void cadastrar_emailDuplicadoNoBanco_lanca409() {
        when(perfilRepository.findById(3L)).thenReturn(Optional.of(perfil(3L, "OPERADOR", "Operador")));
        when(usuarioRepository.existsByEmailIgnoreCase("duplicado@georural.gov.br")).thenReturn(true);

        assertThrows(ConflitoException.class,
                () -> service.cadastrar(new CriarUsuarioRequest("Joao", "duplicado@georural.gov.br", 3L)));

        verifyNoInteractions(keycloakAdminClient);
    }

    @Test
    @DisplayName("Deve lancar RequisicaoInvalidaException (400) quando perfilId for inexistente")
    void cadastrar_perfilInexistente_lanca400() {
        when(perfilRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RequisicaoInvalidaException.class,
                () -> service.cadastrar(new CriarUsuarioRequest("Joao", "joao@georural.gov.br", 99L)));

        verifyNoInteractions(keycloakAdminClient);
    }

    @Test
    @DisplayName("Deve executar compensacao excluindo usuario no Keycloak se a gravacao no Oracle falhar")
    void cadastrar_falhaNoOracle_executaCompensacaoNoKeycloak() {
        when(perfilRepository.findById(3L)).thenReturn(Optional.of(perfil(3L, "OPERADOR", "Operador")));
        when(usuarioRepository.existsByEmailIgnoreCase("erro@georural.gov.br")).thenReturn(false);
        when(keycloakAdminClient.criarUsuario("Erro Banco", "erro@georural.gov.br", "OPERADOR"))
                .thenReturn("sub-orfao-999");
        when(usuarioRepository.save(any(Usuario.class)))
                .thenThrow(new RuntimeException("ORA-00001 falha simulada no Oracle"));

        assertThrows(RuntimeException.class,
                () -> service.cadastrar(new CriarUsuarioRequest("Erro Banco", "erro@georural.gov.br", 3L)));

        verify(keycloakAdminClient, times(1)).excluirUsuario("sub-orfao-999");
        verify(keycloakAdminClient, never()).enviarEmailAcao(any(), any());
    }

    @Test
    @DisplayName("Deve propagar KeycloakIndisponivelException (502) quando Keycloak falhar na criacao")
    void cadastrar_keycloakIndisponivel_propaga502() {
        when(perfilRepository.findById(3L)).thenReturn(Optional.of(perfil(3L, "OPERADOR", "Operador")));
        when(usuarioRepository.existsByEmailIgnoreCase("kc@georural.gov.br")).thenReturn(false);
        when(keycloakAdminClient.criarUsuario(any(), any(), any()))
                .thenThrow(new KeycloakIndisponivelException("Keycloak fora do ar"));

        assertThrows(KeycloakIndisponivelException.class,
                () -> service.cadastrar(new CriarUsuarioRequest("Teste KC", "kc@georural.gov.br", 3L)));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reenvio de convite deve enviar email UPDATE_PASSWORD quando usuario esta ativo e com acao pendente")
    void reenviarConvite_pendente_enviaEmailEAudita() {
        Usuario u = new Usuario();
        u.setId(5L);
        u.setKeycloakId("sub-5");
        u.setEmail("pendente@georural.gov.br");
        u.setStatus("S");
        u.setPerfil(perfil(4L, "ANALISTA", "Analista"));

        when(usuarioRepository.findByIdComPerfil(5L)).thenReturn(Optional.of(u));
        when(keycloakAdminClient.buscarUsuarioPorId("sub-5")).thenReturn(
                new KeycloakUserRepresentation("sub-5", "pendente@georural.gov.br", "pendente@georural.gov.br",
                        "Pendente", "", true, false, List.of("UPDATE_PASSWORD"))
        );

        UsuarioResponse resp = service.reenviarConvite(5L);

        assertEquals(Boolean.TRUE, resp.convitePendente());
        verify(keycloakAdminClient).enviarEmailAcao("sub-5", List.of("UPDATE_PASSWORD"));
        verify(auditoriaService).registrar(eq("REENVIO_CONVITE"), eq("USUARIO"), eq(5L), isNull(), contains("pendente@georural.gov.br"));
    }

    @Test
    @DisplayName("Reenvio de convite deve falhar (400) quando usuario ja definiu senha (sem UPDATE_PASSWORD)")
    void reenviarConvite_semAcaoPendente_lanca400() {
        Usuario u = new Usuario();
        u.setId(5L);
        u.setKeycloakId("sub-5");
        u.setEmail("ativo@georural.gov.br");
        u.setStatus("S");
        u.setPerfil(perfil(4L, "ANALISTA", "Analista"));

        when(usuarioRepository.findByIdComPerfil(5L)).thenReturn(Optional.of(u));
        when(keycloakAdminClient.buscarUsuarioPorId("sub-5")).thenReturn(
                new KeycloakUserRepresentation("sub-5", "ativo@georural.gov.br", "ativo@georural.gov.br",
                        "Ativo", "", true, true, List.of())
        );

        assertThrows(RequisicaoInvalidaException.class, () -> service.reenviarConvite(5L));
        verify(keycloakAdminClient, never()).enviarEmailAcao(any(), any());
    }

    @Test
    @DisplayName("Reenvio de convite deve retornar 404 quando usuario nao existe")
    void reenviarConvite_usuarioInexistente_lanca404() {
        when(usuarioRepository.findByIdComPerfil(999L)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.reenviarConvite(999L));
    }
}