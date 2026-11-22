package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.isap.IsapCategoriaResumoResponse;
import br.com.fatec.georural.dto.response.isap.IsapFeicaoDetalheResponse;
import br.com.fatec.georural.dto.response.isap.IsapImovelConsultaResponse;
import br.com.fatec.georural.dto.response.isap.IsapListaItemResponse;
import br.com.fatec.georural.exception.GlobalExceptionHandler;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.service.indicador.IsapConsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IsapControllerTest {

    private IsapConsultaService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(IsapConsultaService.class);
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new IsapController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/isap deve retornar listagem paginada conforme contrato")
    void listar_deveRetornar200EPagina() throws Exception {
        var item = new IsapListaItemResponse(
                10L, "PR-4109401-AAA111", "Fazenda Pinhal", "Guarapuava",
                new BigDecimal("100.0000"), true, new BigDecimal("25.0000"),
                new BigDecimal("25.0000"), "COM_SOBREPOSICAO", 3L,
                LocalDateTime.of(2026, 11, 22, 14, 0)
        );
        when(service.listar(eq("COM_SOBREPOSICAO"), eq(4109401L), eq("Pinhal"), any()))
                .thenReturn(PaginaResponse.de(new PageImpl<>(List.of(item))));

        mockMvc.perform(get("/api/v1/isap")
                                .param("status", "COM_SOBREPOSICAO")
                                .param("municipioId", "4109401")
                                .param("busca", "Pinhal"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.itens[0].imovelId").value(10))
                        .andExpect(jsonPath("$.itens[0].codigoCar").value("PR-4109401-AAA111"))
                        .andExpect(jsonPath("$.itens[0].status").value("COM_SOBREPOSICAO"))
                        .andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/isap/imoveis/{imovelId} deve retornar resumo com 4 categorias + TOTAL e detalhes")
    void detalharPorId_deveRetornar200EContratoCompleto() throws Exception {
        var resp = new IsapImovelConsultaResponse(
                10L, "PR-4109401-AAA111", "Fazenda Pinhal", "Guarapuava",
                new BigDecimal("100.0000"), 3L, 3L, "1.0",
                LocalDateTime.of(2026, 11, 22, 14, 0),
                "As areas individuais das feicoes podem se sobrepor entre si.",
                List.of(
                        new IsapCategoriaResumoResponse("UC_FEDERAL_ESTADUAL", true,
                                new BigDecimal("25.0000"), new BigDecimal("25.0000"),
                                new BigDecimal("100.0000"), "GRUPO = 'PI'", "2026-11", 8L, "COM_SOBREPOSICAO"),
                        new IsapCategoriaResumoResponse("TOTAL", true,
                                new BigDecimal("25.0000"), new BigDecimal("25.0000"),
                                new BigDecimal("100.0000"), null, null, null, "COM_SOBREPOSICAO")
                ),
                List.of(
                        new IsapFeicaoDetalheResponse(501L, "UC_FEDERAL_ESTADUAL",
                                "PARNA Campos Gerais", "PARNA", "0000.00.0123",
                                new BigDecimal("25.0000"), new BigDecimal("25.0000"))
                )
        );
        when(service.consultarPorImovelId(10L, null)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/isap/imoveis/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imovelId").value(10))
                .andExpect(jsonPath("$.resumo[0].categoria").value("UC_FEDERAL_ESTADUAL"))
                .andExpect(jsonPath("$.resumo[1].categoria").value("TOTAL"))
                .andExpect(jsonPath("$.detalhes[0].nomeFeicao").value("PARNA Campos Gerais"));
    }

    @Test
    @DisplayName("GET /api/v1/isap/imoveis/car/{codigoCar} deve retornar 404 quando imovel nao encontrado")
    void detalharPorCar_quandoInexistente_deveRetornar404() throws Exception {
        when(service.consultarPorCodigoCar("CAR-INEXISTENTE", null))
                .thenThrow(new RecursoNaoEncontradoException("Imovel nao encontrado para o CAR: CAR-INEXISTENTE"));

        mockMvc.perform(get("/api/v1/isap/imoveis/car/CAR-INEXISTENTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.caminho").value("/api/v1/isap/imoveis/car/CAR-INEXISTENTE"));
    }
}