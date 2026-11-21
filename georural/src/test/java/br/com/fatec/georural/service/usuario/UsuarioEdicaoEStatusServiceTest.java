package br.com.fatec.georural.service.usuario;

import br.com.fatec.georural.dto.client.keycloak.KeycloakAdminClient;
import br.com.fatec.georural.dto.request.usuario.EditarUsuarioRequest;
import br.com.fatec.georural.dto.response.usuario.UsuarioResponse;
import br.com.fatec.georural.entity.Perfil;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.repository.PerfilRepository;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.security.UsuarioAutenticado;
import br.com.fatec.georural.security.UsuarioAutenticadoProvider;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioEdicaoEStatusServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PerfilRepository perfilRepository;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private UsuarioAutenticadoProvider usuarioAutenticadoProvider;

    @Mock
    private TransactionTemplate transactionTemplate;

    private UsuarioEdicaoService edicaoService;
    private UsuarioStatusService statusService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });

        edicaoService = new UsuarioEdicaoService(
                usuarioRepository,
                perfilRepository,
                keycloakAdminClient,
                auditoriaService,
                usuarioAutenticadoProvider,
                transactionTemplate
        );

        statusService = new UsuarioStatusService(
                usuarioRepository,
                keycloakAdminClient,
                auditoriaService,
                usuarioAutenticadoProvider,
                transactionTemplate
        );
    }

    private Perfil perfil(Long id, String funcao) {
        Perfil p = new Perfil();
        p.setId(id);
        p.setFuncao(funcao);
        p.setNome(funcao);
        return p;
    }

    private Usuario usuario(Long id, String sub, String nome, String status, Perfil perfil) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setKeycloakId(sub);
        u.setNome(nome);
        u.setEmail(nome.toLowerCase().replace(" ", ".") + "@georural.invalid");
        u.setStatus(status);
        u.setPerfil(perfil);
        return u;
    }

    @Test
    @DisplayName("Troca de perfil deve atualizar banco, trocar role no Keycloak, revogar sessoes e auditar")
    void editar_trocaDePerfil_sincronizaKeycloakERevogaSessoes() {
        Perfil operador = perfil(3L, "OPERADOR");
        Perfil gestor = perfil(2L, "GESTOR");
        Usuario alvo = usuario(4L, "sub-op", "Operador Teste", "S", operador);

        when(perfilRepository.findById(2L)).thenReturn(Optional.of(gestor));
        when(usuarioRepository.findByIdComPerfil(4L)).thenReturn(Optional.of(alvo));
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResponse resp = edicaoService.editar(4L, new EditarUsuarioRequest("Operador Promovido", 2L));

        assertEquals("GESTOR", resp.perfilFuncao());
        assertEquals("Operador Promovido", resp.nome());
        verify(keycloakAdminClient).trocarRealmRole("sub-op", "OPERADOR", "GESTOR");
        verify(keycloakAdminClient).revogarSessoes("sub-op");
        verify(keycloakAdminClient).atualizarNome("sub-op", "Operador Promovido");
        verify(auditoriaService).registrar(eq("UPDATE"), eq("USUARIO"), eq(4L), contains("OPERADOR"), contains("GESTOR"));
    }

    @Test
    @DisplayName("Admin nao pode rebaixar o proprio perfil (409 Conflict)")
    void editar_autoRebaixamentoAdmin_lanca409() {
        Perfil admin = perfil(1L, "ADMIN");
        Perfil gestor = perfil(2L, "GESTOR");
        Usuario adminLogado = usuario(1L, "sub-admin1", "Admin Um", "S", admin);

        when(perfilRepository.findById(2L)).thenReturn(Optional.of(gestor));
        when(usuarioRepository.findByIdComPerfil(1L)).thenReturn(Optional.of(adminLogado));
        when(usuarioAutenticadoProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(1L, "sub-admin1", "S", "ADMIN")));

        assertThrows(ConflitoException.class,
                () -> edicaoService.editar(1L, new EditarUsuarioRequest(null, 2L)));

        verifyNoInteractions(keycloakAdminClient);
    }

    @Test
    @DisplayName("Nao permite rebaixar o ultimo administrador ativo do sistema (409 Conflict)")
    void editar_ultimoAdminAtivo_lanca409() {
        Perfil admin = perfil(1L, "ADMIN");
        Perfil gestor = perfil(2L, "GESTOR");
        Usuario unicoAdmin = usuario(1L, "sub-admin1", "Admin Unico", "S", admin);

        when(perfilRepository.findById(2L)).thenReturn(Optional.of(gestor));
        when(usuarioRepository.findByIdComPerfil(1L)).thenReturn(Optional.of(unicoAdmin));
        when(usuarioAutenticadoProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(99L, "sub-outro-admin", "S", "ADMIN")));
        when(usuarioRepository.findAdminsAtivosComLock()).thenReturn(List.of(unicoAdmin));

        assertThrows(ConflitoException.class,
                () -> edicaoService.editar(1L, new EditarUsuarioRequest(null, 2L)));

        verifyNoInteractions(keycloakAdminClient);
    }

    @Test
    @DisplayName("Admin nao pode desativar a propria conta (409 Conflict)")
    void desativar_autoDesativacao_lanca409() {
        Usuario adminLogado = usuario(1L, "sub-admin1", "Admin Um", "S", perfil(1L, "ADMIN"));
        when(usuarioRepository.findByIdComPerfil(1L)).thenReturn(Optional.of(adminLogado));
        when(usuarioAutenticadoProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(1L, "sub-admin1", "S", "ADMIN")));

        assertThrows(ConflitoException.class, () -> statusService.desativar(1L));
        verifyNoInteractions(keycloakAdminClient);
    }

    @Test
    @DisplayName("Nao permite desativar o ultimo administrador ativo do sistema (409 Conflict)")
    void desativar_ultimoAdminAtivo_lanca409() {
        Usuario unicoAdmin = usuario(1L, "sub-admin1", "Admin Unico", "S", perfil(1L, "ADMIN"));
        when(usuarioRepository.findByIdComPerfil(1L)).thenReturn(Optional.of(unicoAdmin));
        when(usuarioAutenticadoProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(99L, "sub-outro", "S", "ADMIN")));
        when(usuarioRepository.findAdminsAtivosComLock()).thenReturn(List.of(unicoAdmin));

        assertThrows(ConflitoException.class, () -> statusService.desativar(1L));
        verifyNoInteractions(keycloakAdminClient);
    }

    @Test
    @DisplayName("Desativar e reativar sincronizam banco, Keycloak, revogacao de sessoes e auditoria")
    void desativarEReativar_sucesso() {
        Usuario alvo = usuario(5L, "sub-analista", "Analista Teste", "S", perfil(4L, "ANALISTA"));
        when(usuarioRepository.findByIdComPerfil(5L)).thenReturn(Optional.of(alvo));
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        when(usuarioAutenticadoProvider.obterOpcional())
                .thenReturn(Optional.of(new UsuarioAutenticado(1L, "sub-admin", "S", "ADMIN")));

        UsuarioResponse desativado = statusService.desativar(5L);
        assertEquals("N", desativado.status());
        verify(keycloakAdminClient).alterarStatus("sub-analista", false);
        verify(keycloakAdminClient).revogarSessoes("sub-analista");
        verify(auditoriaService).registrar(eq("DESATIVACAO"), eq("USUARIO"), eq(5L), contains("\"S\""), contains("\"N\""));

        UsuarioResponse reativado = statusService.reativar(5L);
        assertEquals("S", reativado.status());
        verify(keycloakAdminClient).alterarStatus("sub-analista", true);
        verify(auditoriaService).registrar(eq("REATIVACAO"), eq("USUARIO"), eq(5L), contains("\"N\""), contains("\"S\""));
    }
}