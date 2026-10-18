package br.com.fatec.georural.controller.ingestao;

import br.com.fatec.georural.controller.IngestaoApiController;
import br.com.fatec.georural.dto.ingestao.FonteIngestaoResponse;
import br.com.fatec.georural.dto.ingestao.ImportacaoRequest;
import br.com.fatec.georural.dto.ingestao.ImportacaoResponse;
import br.com.fatec.georural.dto.ingestao.ItemDisponivelResponse;
import br.com.fatec.georural.dto.ingestao.ResultadoImportacaoItem;
import br.com.fatec.georural.exception.FalhaComunicacaoApiException;
import br.com.fatec.georural.exception.IngestaoApiExceptionHandler;
import br.com.fatec.georural.service.ingestao.AcompanhamentoIngestaoService;
import br.com.fatec.georural.service.ingestao.ImportacaoApiService;
import br.com.fatec.georural.service.ingestao.IngestaoConsultaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IngestaoApiControllerTest {

    @Mock IngestaoConsultaService consultaService;
    @Mock ImportacaoApiService importacaoService;
    @Mock AcompanhamentoIngestaoService acompanhamentoService;

    @InjectMocks
    IngestaoApiController controller;

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new IngestaoApiExceptionHandler())
                .build();
    }

    @Test
    void get_fontes_ok() throws Exception {
        when(consultaService.listarFontesComApi())
                .thenReturn(List.of(new FonteIngestaoResponse(1L, "INPE", "INPE", List.of())));

        mvc.perform(get("/api/ingestao/fontes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("INPE"));
    }

    @Test
    void get_disponiveis_ok() throws Exception {
        when(importacaoService.listarDisponiveis(10L))
                .thenReturn(List.of(new ItemDisponivelResponse(
                        "2026-10-18", "focos_calor_2026-10-18.geojson", "Coleta de 2026-10-18", false)));

        mvc.perform(get("/api/ingestao/conjuntos/10/disponiveis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].identificador").value("2026-10-18"))
                .andExpect(jsonPath("$[0].jaRecebido").value(false));
    }

    @Test
    void post_importar_ok_traz_notificacoes() throws Exception {
        ResultadoImportacaoItem item = ResultadoImportacaoItem.sucesso(
                "2026-10-18", "focos_calor_2026-10-18.geojson", 10, 99L);
        when(importacaoService.importar(eq(10L), anyList()))
                .thenReturn(ImportacaoResponse.de(List.of(item)));

        String body = mapper.writeValueAsString(new ImportacaoRequest(List.of("2026-10-18")));

        mvc.perform(post("/api/ingestao/conjuntos/10/importar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importados").value(1))
                .andExpect(jsonPath("$.notificacoes[0].tipo").value("success"));
    }

    @Test
    void post_importar_falha_comunicacao_vira_502() throws Exception {
        when(importacaoService.importar(eq(10L), anyList()))
                .thenThrow(new FalhaComunicacaoApiException("Falha ao consultar a API externa: timeout"));

        String body = mapper.writeValueAsString(new ImportacaoRequest(List.of("2026-10-18")));

        mvc.perform(post("/api/ingestao/conjuntos/10/importar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.path").value("/api/ingestao/conjuntos/10/importar"));
    }
}