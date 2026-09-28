package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.exception.GlobalExceptionHandler;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.service.CalculoIAEService;
import br.com.fatec.georural.service.ConsultaIAEService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IAEControllerTest {

    private ConsultaIAEService consultaService;
    private CalculoIAEService calculoService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        consultaService = Mockito.mock(ConsultaIAEService.class);
        calculoService = Mockito.mock(CalculoIAEService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new IAEController(consultaService, calculoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void consultar_deveRetornar404_quandoNaoCalculado() throws Exception {
        when(consultaService.consultarPorImovel(99L))
                .thenThrow(new RecursoNaoEncontradoException("Nenhum resultado de IAE calculado para o imóvel 99"));

        mockMvc.perform(get("/api/indicadores/iae/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void calcular_deveRetornar201EResultado() throws Exception {
        IAEResponse resposta = new IAEResponse(41L, "IAE", null, null, null, null, List.of());
        when(calculoService.calcularSobreposicaoEmbargo(41L)).thenReturn(resposta);

        mockMvc.perform(post("/api/indicadores/iae/41"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imovelId").value(41));
    }

    @Test
    void calcular_deveRetornar409_quandoJaCalculado() throws Exception {
        when(calculoService.calcularSobreposicaoEmbargo(41L))
                .thenThrow(new ConflitoException("IAE já calculado para o imóvel 41"));

        mockMvc.perform(post("/api/indicadores/iae/41"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}