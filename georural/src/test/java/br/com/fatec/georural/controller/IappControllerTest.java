package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.iapp.IappListaItemResponse;
import br.com.fatec.georural.dto.response.iapp.IappResultadoResponse;
import br.com.fatec.georural.exception.GlobalExceptionHandler;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.service.indicador.IappConsultaService;
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

class IappControllerTest {

    private IappConsultaService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(IappConsultaService.class);
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new IappController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/iapp deve retornar listagem paginada conforme contrato")
    void listar_deveRetornar200EPagina() throws Exception {
        var item = new IappListaItemResponse(
                100L, 10L, "PR-4109401-IAPP01", "Fazenda Rio Verde",
                4109401L, "Guarapuava", 3L,
                new BigDecimal("20.0000"), new BigDecimal("15.0000"), new BigDecimal("5.0000"),
                new BigDecimal("75.0000"), new BigDecimal("100.0000"),
                "CALCULADO", "APP preservada em 75,00% (passivo de 5,0000 ha).",
                "S", LocalDateTime.of(2026, 11, 22, 18, 0)
        );
        when(service.listar(eq("CALCULADO"), eq(4109401L), eq("Rio"), eq(3L), any()))
                .thenReturn(PaginaResponse.de(new PageImpl<>(List.of(item))));

        mockMvc.perform(get("/api/v1/iapp")
                        .param("status", "CALCULADO")
                        .param("municipioId", "4109401")
                        .param("busca", "Rio")
                        .param("versaoId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].iappId").value(100))
                .andExpect(jsonPath("$.itens[0].codigoCar").value("PR-4109401-IAPP01"))
                .andExpect(jsonPath("$.itens[0].appTotalHa").value(20.0))
                .andExpect(jsonPath("$.itens[0].appVegetadaHa").value(15.0))
                .andExpect(jsonPath("$.itens[0].passivoHa").value(5.0))
                .andExpect(jsonPath("$.itens[0].iappPct").value(75.0))
                .andExpect(jsonPath("$.itens[0].status").value("CALCULADO"))
                .andExpect(jsonPath("$.totalItens").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/iapp/imoveis/{imovelId} deve retornar detalhes, rastreabilidade e GeoJSON")
    void detalharPorId_deveRetornar200EContratoCompleto() throws Exception {
        var resp = new IappResultadoResponse(
                100L, 10L, "PR-4109401-IAPP01", "Fazenda Rio Verde",
                4109401L, "Guarapuava", 3L, 2, 4L, 5L, 12L, 8L, "1.0",
                new BigDecimal("20.0000"), new BigDecimal("15.0000"), new BigDecimal("5.0000"),
                new BigDecimal("75.0000"), new BigDecimal("100.0000"),
                "TODOS", "TODOS", "2026-10", "2026-10",
                "CALCULADO", "APP preservada em 75,00% (passivo de 5,0000 ha).",
                "S", LocalDateTime.of(2026, 11, 22, 18, 0),
                "{\"type\":\"Polygon\",\"coordinates\":[]}",
                "{\"type\":\"Polygon\",\"coordinates\":[]}"
        );
        when(service.consultarPorImovelId(10L, null, true)).thenReturn(resp);

        mockMvc.perform(get("/api/v1/iapp/imoveis/10")
                        .param("incluirGeoJson", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imovelId").value(10))
                .andExpect(jsonPath("$.status").value("CALCULADO"))
                .andExpect(jsonPath("$.appVegetadaGeoJson.type").value("Polygon"))
                .andExpect(jsonPath("$.passivoGeoJson.type").value("Polygon"));
    }

    @Test
    @DisplayName("GET /api/v1/iapp/imoveis/car/{codigoCar} deve suportar SEM_APP e CAMADA_INDISPONIVEL com HTTP 200")
    void detalharPorCar_semAppECamadaIndisponivel_deveRetornar200() throws Exception {
        var respCamadaIndisp = new IappResultadoResponse(
                102L, 12L, "PR-4109401-INDISP", "Fazenda Oeste",
                4109401L, "Guarapuava", 3L, 2, 4L, null, 12L, 8L, "1.0",
                new BigDecimal("20.0000"), null, null, null, new BigDecimal("100.0000"),
                "TODOS", "TODOS", "2026-10", null,
                "CAMADA_INDISPONIVEL", "Camada de vegetacao nativa vigente indisponivel para o calculo do IAPP.",
                "S", LocalDateTime.of(2026, 11, 22, 18, 10),
                null, null
        );
        when(service.consultarPorCodigoCar("PR-4109401-INDISP", null, false)).thenReturn(respCamadaIndisp);

        mockMvc.perform(get("/api/v1/iapp/imoveis/car/PR-4109401-INDISP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAMADA_INDISPONIVEL"))
                .andExpect(jsonPath("$.iappPct").doesNotExist())
                .andExpect(jsonPath("$.passivoHa").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/iapp/imoveis/{imovelId} deve retornar 404 quando não encontrado")
    void detalharPorId_naoEncontrado_deveRetornar404() throws Exception {
        when(service.consultarPorImovelId(999L, null, false))
                .thenThrow(new RecursoNaoEncontradoException("Imovel nao encontrado: 999"));

        mockMvc.perform(get("/api/v1/iapp/imoveis/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}