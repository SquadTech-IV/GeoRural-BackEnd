package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ArquivoBrutoResumoResponse;
import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.exception.GlobalExceptionHandler;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.service.storage.ConsultaArquivoBrutoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsultaArquivoBrutoControllerTest {

    private static final String URL = "/api/arquivos/brutos";
    private static final String HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private ConsultaArquivoBrutoService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ConsultaArquivoBrutoService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConsultaArquivoBrutoController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static PaginaResponse<ArquivoBrutoResumoResponse> pagina() {
        ArquivoBrutoResumoResponse item = new ArquivoBrutoResumoResponse(
                10L, 1L, "CAR - Guarapuava", "dados.csv", "CSV", 3L, HASH, "SHA-256", "UPLOAD", "RECEBIDO",
                2L, "Usuário OPERADOR", LocalDateTime.of(2026, 10, 22, 16, 0));
        return new PaginaResponse<>(List.of(item), 0, 20, 1L, 1);
    }

    @Test
    void listar_semParametros_deveUsarPaginaZeroTamanhoVinteERetornarOsDados() throws Exception {
        when(service.listar(isNull(), isNull(), eq(0), eq(20))).thenReturn(pagina());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.totalItens").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.itens[0].id").value(10))
                .andExpect(jsonPath("$.itens[0].conjuntoNome").value("CAR - Guarapuava"))
                .andExpect(jsonPath("$.itens[0].hash").value(HASH))
                .andExpect(jsonPath("$.itens[0].responsavelNome").value("Usuário OPERADOR"))
                .andExpect(jsonPath("$.itens[0].status").value("RECEBIDO"))
                .andExpect(jsonPath("$.itens[0].pathArmazenamento").doesNotExist());
    }

    @Test
    void listar_comFiltrosEPaginacao_devePassarOsParametrosAoServico() throws Exception {
        when(service.listar(eq(1L), eq("RECEBIDO"), eq(2), eq(50))).thenReturn(pagina());

        mockMvc.perform(get(URL).param("conId", "1").param("status", "RECEBIDO").param("pagina", "2").param("tamanho", "50"))
                .andExpect(status().isOk());

        verify(service).listar(1L, "RECEBIDO", 2, 50);
    }

    @Test
    void listar_conjuntoNaoNumerico_deveRetornar400() throws Exception {
        mockMvc.perform(get(URL).param("conId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Parâmetro 'conId' deve ser um número válido."));

        verify(service, never()).listar(isNull(), isNull(), anyInt(), anyInt());
    }

    @Test
    void listar_paginaNaoNumerica_deveRetornar400() throws Exception {
        mockMvc.perform(get(URL).param("pagina", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_parametroRejeitadoPeloServico_deveRetornar400ComAMensagem() throws Exception {
        when(service.listar(isNull(), isNull(), eq(0), eq(500)))
                .thenThrow(new RequisicaoInvalidaException("O tamanho da página deve estar entre 1 e 100."));

        mockMvc.perform(get(URL).param("tamanho", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("O tamanho da página deve estar entre 1 e 100."));
    }

    @Test
    void listar_erroInterno_deveRetornar500SemVazarOsDetalhes() throws Exception {
        when(service.listar(isNull(), isNull(), eq(0), eq(20)))
                .thenThrow(new IllegalStateException("detalhe interno que não deve vazar"));

        mockMvc.perform(get(URL))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensagem").value("Erro interno no servidor."));
    }
}