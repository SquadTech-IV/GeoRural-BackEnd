package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoResumoResponse;
import br.com.fatec.georural.exception.GlobalExceptionHandler;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.service.ArquivoDatalakeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ArquivoDatalakeControllerTest {

    private ArquivoDatalakeService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ArquivoDatalakeService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ArquivoDatalakeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listar_deveRetornar200EAlista() throws Exception {
        var item = new ArquivoResumoResponse(
                1L, "CAR - Guarapuava PR", "CAR_-_Guarapuava_PR.zip",
                "CAR", "ZIP", 6, "AGUARDANDO", LocalDateTime.now());
        when(service.listar(any(), any())).thenReturn(List.of(item));

        mockMvc.perform(get("/api/arquivos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("CAR - Guarapuava PR"))
                .andExpect(jsonPath("$[0].qtdArquivos").value(6));
    }

    @Test
    void detalhe_deveRetornar404_quandoNaoExiste() throws Exception {
        when(service.detalhe(any()))
                .thenThrow(new RecursoNaoEncontradoException("Arquivo nao encontrado: 999"));

        mockMvc.perform(get("/api/arquivos/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").value("Arquivo nao encontrado: 999"))
                .andExpect(jsonPath("$.caminho").value("/api/arquivos/999"));
    }
}