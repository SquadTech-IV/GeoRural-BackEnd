package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Indicador;
import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse.Resumo;
import br.com.fatec.georural.service.comparacao.ComparacaoVersaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ComparacaoVersaoControllerTest {

    private ComparacaoVersaoService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ComparacaoVersaoService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ComparacaoVersaoController(service)).build();
    }

    @Test
    @DisplayName("GET /api/v1/linhagem/comparacao repassa os filtros ao servico e devolve o resumo")
    void comparar() throws Exception {
        var resposta = new ComparacaoVersaoResponse(
                new Indicador("IAE", "Índice de Área Embargada", "%"), null, null,
                new Resumo(3, 1, 1, 1, 0), List.of(), List.of(),
                PaginaResponse.de(List.of(), 0, 20, 0));
        when(service.comparar("IAE", 1L, 2L, true, 0, 20)).thenReturn(resposta);

        mockMvc.perform(get("/api/v1/linhagem/comparacao")
                        .param("indicador", "IAE").param("versaoA", "1").param("versaoB", "2")
                        .param("apenasAlterados", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicador.sigla").value("IAE"))
                .andExpect(jsonPath("$.resumo.alterados").value(1));

        verify(service).comparar("IAE", 1L, 2L, true, 0, 20);
    }

    @Test
    @DisplayName("Sem versaoA ou versaoB a requisicao e rejeitada com 400")
    void semParametros() throws Exception {
        mockMvc.perform(get("/api/v1/linhagem/comparacao").param("indicador", "IAE").param("versaoA", "1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
