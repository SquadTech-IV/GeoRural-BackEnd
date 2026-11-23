package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.client.keycloak.KeycloakAdminClient;
import br.com.fatec.georural.dto.client.keycloak.KeycloakUserRepresentation;
import br.com.fatec.georural.dto.response.usuario.DivergenciaUsuarioResponse;
import br.com.fatec.georural.dto.response.usuario.PerfilAcessoResponse;
import br.com.fatec.georural.dto.response.usuario.UsuarioResponse;
import br.com.fatec.georural.entity.Perfil;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.GlobalExceptionHandler;
import br.com.fatec.georural.exception.KeycloakIndisponivelException;
import br.com.fatec.georural.repository.UsuarioRepository;
import br.com.fatec.georural.service.usuario.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UsuarioControllerEDivergenciaTest {

    private UsuarioCadastroService cadastroService;
    private UsuarioEdicaoService edicaoService;
    private UsuarioStatusService statusService;
    private UsuarioConsultaService consultaService;
    private UsuarioDivergenciaService divergenciaService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cadastroService = mock(UsuarioCadastroService.class);
        edicaoService = mock(UsuarioEdicaoService.class);
        statusService = mock(UsuarioStatusService.class);
        consultaService = mock(UsuarioConsultaService.class);
        divergenciaService = mock(UsuarioDivergenciaService.class);

        JsonMapper jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        UsuarioController controller = new UsuarioController(
                cadastroService,
                edicaoService,
                statusService,
                consultaService,
                divergenciaService
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .build();
    }

    @Test
    @DisplayName("PATCH /api/usuarios/{id} rejeita tentativa de alterar email ou campo desconhecido com 400")
    void patchUsuario_comCampoEmail_deveRetornar400() throws Exception {
        mockMvc.perform(patch("/api/usuarios/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Novo Nome\",\"email\":\"hacker@georural.invalid\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(edicaoService);
    }

    @Test
    @DisplayName("GET /api/usuarios retorna PaginaResponse padronizado com usuarios filtrados")
    void listarUsuarios_deveRetornarPaginaResponse() throws Exception {
        UsuarioResponse item = new UsuarioResponse(
                1L, "Administrador", "admin@georural.invalid", "S",
                1L, "ADMIN", "Administrador", null, LocalDateTime.now(), null, null
        );
        when(consultaService.listar(eq("admin"), eq(1L), eq("S"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        mockMvc.perform(get("/api/usuarios")
                        .param("busca", "admin")
                        .param("perfilId", "1")
                        .param("status", "S"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..id").value(1))
                .andExpect(jsonPath("$..email").value("admin@georural.invalid"))
                .andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    @DisplayName("GET /api/usuarios/perfis lista os perfis de acesso")
    void listarPerfis_deveRetornarLista() throws Exception {
        when(consultaService.listarPerfis()).thenReturn(List.of(
                new PerfilAcessoResponse(1L, "ADMIN", "Administrador"),
                new PerfilAcessoResponse(2L, "GESTOR", "Gestor")
        ));

        mockMvc.perform(get("/api/usuarios/perfis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].funcao").value("ADMIN"))
                .andExpect(jsonPath("$[1].funcao").value("GESTOR"));
    }

    @Test
    @DisplayName("Retorna 502 Bad Gateway padronizado quando Keycloak estiver indisponivel")
    void criarUsuario_keycloakIndisponivel_retorna502() throws Exception {
        when(cadastroService.cadastrar(any()))
                .thenThrow(new KeycloakIndisponivelException("Keycloak indisponível."));

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Teste\",\"email\":\"teste@georural.gov.br\",\"perfilId\":3}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.mensagem").value("Keycloak indisponível."));
    }

    @Test
    @DisplayName("UsuarioDivergenciaService identifica usuario sem sub, perfil divergente e status divergente")
    void divergencias_deveDetectarOsTresTiposDeDivergencia() {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        KeycloakAdminClient kc = mock(KeycloakAdminClient.class);

        Perfil admin = new Perfil();
        admin.setId(1L);
        admin.setFuncao("ADMIN");

        Perfil operador = new Perfil();
        operador.setId(3L);
        operador.setFuncao("OPERADOR");

        Usuario uSemSub = new Usuario();
        uSemSub.setId(10L);
        uSemSub.setKeycloakId("sub-inexistente");
        uSemSub.setEmail("sem.sub@georural.invalid");
        uSemSub.setNome("Sem Sub");
        uSemSub.setStatus("S");
        uSemSub.setPerfil(operador);

        Usuario uDivergente = new Usuario();
        uDivergente.setId(11L);
        uDivergente.setKeycloakId("sub-div");
        uDivergente.setEmail("div@georural.invalid");
        uDivergente.setNome("Divergente");
        uDivergente.setStatus("S");
        uDivergente.setPerfil(admin);

        when(repo.findAllComPerfil()).thenReturn(List.of(uSemSub, uDivergente));
        when(kc.listarUsuarios()).thenReturn(List.of(
                new KeycloakUserRepresentation("sub-div", "div@georural.invalid", "div@georural.invalid",
                        "Divergente", "", false, true, List.of())
        ));
        when(kc.listarNomesRealmRolesDoUsuario("sub-div")).thenReturn(Set.of("OPERADOR"));

        UsuarioDivergenciaService divService = new UsuarioDivergenciaService(repo, kc);
        List<DivergenciaUsuarioResponse> lista = divService.listarDivergencias();

        assertEquals(3, lista.size());
        assertEquals(UsuarioDivergenciaService.TIPO_SEM_SUB, lista.get(0).tipoDivergencia());
        assertEquals(UsuarioDivergenciaService.TIPO_PERFIL_DIVERGENTE, lista.get(1).tipoDivergencia());
        assertEquals(UsuarioDivergenciaService.TIPO_STATUS_DIVERGENTE, lista.get(2).tipoDivergencia());
    }
}