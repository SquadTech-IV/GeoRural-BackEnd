package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.indicador.IRLResponse;
import br.com.fatec.georural.exception.GlobalExceptionHandler;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.service.CalculoIRLGeoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IRLControllerTest {

    private CalculoIRLGeoService calculoService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        calculoService = Mockito.mock(CalculoIRLGeoService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new IRLController(calculoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void recalcular_deveRetornar200EResultado() throws Exception {
        IRLResponse resposta = new IRLResponse(1L, "IRL", null, null, null, null, "ruim", null, null, "v1");
        when(calculoService.calcularReservaLegal(1L)).thenReturn(resposta);

        mockMvc.perform(post("/api/indicadores/irl/1/recalcular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imovelId").value(1))
                .andExpect(jsonPath("$.indicadorSigla").value("IRL"))
                .andExpect(jsonPath("$.situacao").value("ruim"));
    }

    @Test
    void recalcular_deveRetornar404_quandoImovelNaoExiste() throws Exception {
        when(calculoService.calcularReservaLegal(99L))
                .thenThrow(new RecursoNaoEncontradoException("Imóvel não encontrado: 99"));

        mockMvc.perform(post("/api/indicadores/irl/99/recalcular"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}