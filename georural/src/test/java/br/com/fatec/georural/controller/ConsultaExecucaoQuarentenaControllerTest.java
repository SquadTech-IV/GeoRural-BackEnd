package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.qualidade.ExecucaoConsultaResponse;
import br.com.fatec.georural.dto.response.qualidade.QuarentenaConsultaResponse;
import br.com.fatec.georural.service.qualidade.ConsultaExecucaoQuarentenaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConsultaExecucaoQuarentenaControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ConsultaExecucaoQuarentenaService consultaService;

    @InjectMocks
    private ConsultaExecucaoQuarentenaController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/qualidade/execucoes - Deve retornar lista paginada de execuções com contagens")
    void deveListarExecucoesPaginado() throws Exception {
        ExecucaoConsultaResponse resp = ExecucaoConsultaResponse.builder()
                .id(1L)
                .nomeConjunto("Embargos Ibama")
                .status("COM_RESSALVA")
                .registrosRecebidos(2158L)
                .registrosAprovados(2150L)
                .registrosRejeitados(8L)
                .inicio(LocalDateTime.now())
                .build();

        when(consultaService.listarExecucoes(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(resp)));

        mockMvc.perform(get("/api/v1/qualidade/execucoes")
                        .param("conjuntoId", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].status").value("COM_RESSALVA"))
                .andExpect(jsonPath("$.content[0].registrosRecebidos").value(2158))
                .andExpect(jsonPath("$.content[0].registrosAprovados").value(2150))
                .andExpect(jsonPath("$.content[0].registrosRejeitados").value(8));
    }

    @Test
    @DisplayName("GET /api/v1/qualidade/quarentena - Deve retornar registros descartados filtrados")
    void deveConsultarQuarentenaComFiltros() throws Exception {
        QuarentenaConsultaResponse qResp = QuarentenaConsultaResponse.builder()
                .quarentenaId(100L)
                .execucaoId(1L)
                .linhaOrigem(45L)
                .codigoRegra("VAL-007")
                .nomeRegra("Geometria Inválida")
                .motivoErro("Geometria com auto-intersecção irreparável")
                .situacao("PENDENTE")
                .build();

        when(consultaService.consultarQuarentena(eq(1L), eq(1L), eq("PENDENTE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(qResp)));

        mockMvc.perform(get("/api/v1/qualidade/quarentena")
                        .param("conjuntoId", "1")
                        .param("execucaoId", "1")
                        .param("situacao", "PENDENTE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].quarentenaId").value(100))
                .andExpect(jsonPath("$.content[0].codigoRegra").value("VAL-007"))
                .andExpect(jsonPath("$.content[0].linhaOrigem").value(45));
    }
}