package br.com.fatec.georural.controller;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.exception.ArquivoDuplicadoException;
import br.com.fatec.georural.exception.GlobalExceptionHandler;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.service.storage.RecebimentoArquivoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecebimentoArquivoControllerTest {

    private static final String URL = "/api/arquivos/brutos/upload";
    private static final String HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    private RecebimentoArquivoService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(RecebimentoArquivoService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RecebimentoArquivoController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockMultipartFile arquivo() {
        return new MockMultipartFile("arquivo", "dados.csv", "text/csv", "abc".getBytes(StandardCharsets.UTF_8));
    }

    private static ArquivoBruto registro() {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(1L);

        ArquivoBruto registro = new ArquivoBruto();
        registro.setId(10L);
        registro.setConjunto(conjunto);
        registro.setNome("dados.csv");
        registro.setFormato("CSV");
        registro.setTamanhoBytes(3L);
        registro.setHash(HASH);
        registro.setStatus("RECEBIDO");
        registro.setPathArmazenamento("conjuntos/1/brutos/2026/10/" + HASH + "_dados.csv");
        registro.setDataUpload(LocalDateTime.of(2026, 10, 22, 16, 0));
        return registro;
    }

    // ---------------------------------------------------------------------- 201

    @Test
    void receber_deveRetornar201ComOsDadosDoRegistroSemExporOCaminhoDoObjeto() throws Exception {
        when(service.receber(eq(1L), eq(2L), eq("dados.csv"), any(InputStream.class))).thenReturn(registro());

        mockMvc.perform(multipart(URL).file(arquivo()).param("conId", "1").param("usuId", "2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.conjuntoId").value(1))
                .andExpect(jsonPath("$.nome").value("dados.csv"))
                .andExpect(jsonPath("$.formato").value("CSV"))
                .andExpect(jsonPath("$.tamanhoBytes").value(3))
                .andExpect(jsonPath("$.hash").value(HASH))
                .andExpect(jsonPath("$.algoritmoHash").value("SHA-256"))
                .andExpect(jsonPath("$.canalEntrada").value("UPLOAD"))
                .andExpect(jsonPath("$.status").value("RECEBIDO"))
                .andExpect(jsonPath("$.pathArmazenamento").doesNotExist());
    }

    // ---------------------------------------------------------------------- 400

    @Test
    void receber_semOArquivo_deveRetornar400SemChamarOServico() throws Exception {
        mockMvc.perform(multipart(URL).param("conId", "1").param("usuId", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.caminho").value(URL));

        verifyNoInteractions(service);
    }

    @Test
    void receber_semOConjunto_deveRetornar400ComAMensagemDoServico() throws Exception {
        when(service.receber(isNull(), any(), any(), any(InputStream.class)))
                .thenThrow(new RequisicaoInvalidaException("Identificador do conjunto é obrigatório."));

        mockMvc.perform(multipart(URL).file(arquivo()).param("usuId", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("Identificador do conjunto é obrigatório."));
    }

    @Test
    void receber_formatoNaoAceito_deveRetornar400() throws Exception {
        when(service.receber(eq(1L), eq(2L), any(), any(InputStream.class)))
                .thenThrow(new RequisicaoInvalidaException("Formato .json não é aceito."));

        mockMvc.perform(multipart(URL).file(arquivo()).param("conId", "1").param("usuId", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Formato .json não é aceito."));
    }

    @Test
    void receber_conjuntoComIdentificadorNaoNumerico_deveRetornar400() throws Exception {
        mockMvc.perform(multipart(URL).file(arquivo()).param("conId", "abc").param("usuId", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Parâmetro 'conId' deve ser um número válido."));

        verify(service, never()).receber(any(), any(), any(), any());
    }

    // ---------------------------------------------------------------------- 404

    @Test
    void receber_conjuntoOuUsuarioInexistente_deveRetornar404() throws Exception {
        when(service.receber(eq(999L), eq(2L), any(), any(InputStream.class)))
                .thenThrow(new RecursoNaoEncontradoException("Conjunto de dados não encontrado: 999"));

        mockMvc.perform(multipart(URL).file(arquivo()).param("conId", "999").param("usuId", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensagem").value("Conjunto de dados não encontrado: 999"));
    }

    // ---------------------------------------------------------------------- 409

    @Test
    void receber_arquivoDuplicado_deveRetornar409() throws Exception {
        when(service.receber(eq(1L), eq(2L), any(), any(InputStream.class)))
                .thenThrow(new ArquivoDuplicadoException(1L, HASH));

        mockMvc.perform(multipart(URL).file(arquivo()).param("conId", "1").param("usuId", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ---------------------------------------------------------------------- 500

    @Test
    void receber_erroInterno_deveRetornar500SemVazarADescricaoDoErro() throws Exception {
        when(service.receber(eq(1L), eq(2L), any(), any(InputStream.class)))
                .thenThrow(new IllegalStateException("detalhe interno que não deve vazar"));

        mockMvc.perform(multipart(URL).file(arquivo()).param("conId", "1").param("usuId", "2"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.mensagem").value("Erro interno no servidor."));
    }
}