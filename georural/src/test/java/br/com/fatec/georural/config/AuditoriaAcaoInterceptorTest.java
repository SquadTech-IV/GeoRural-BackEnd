package br.com.fatec.georural.config;

import br.com.fatec.georural.service.auditoria.AuditoriaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class AuditoriaAcaoInterceptorTest {

    private AuditoriaService auditoriaService;
    private AuditoriaAcaoInterceptor interceptor;

    @BeforeEach
    void setUp() {
        auditoriaService = mock(AuditoriaService.class);
        interceptor = new AuditoriaAcaoInterceptor(auditoriaService, new ObjectMapper());
    }

    private void executar(String metodo, String caminho, int status, String... params) {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, caminho);
        for (int i = 0; i < params.length; i += 2) {
            req.setParameter(params[i], params[i + 1]);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        res.setStatus(status);
        interceptor.afterCompletion(req, res, new Object(), null);
    }

    @Test
    @DisplayName("Upload bem-sucedido registra UPLOAD com o conjunto")
    void upload_registra() {
        executar("POST", "/api/arquivos/brutos/upload", 201, "conId", "7");

        verify(auditoriaService).registrarIndependente(eq("UPLOAD"), eq("CONJUNTO_DADOS"), eq(7L), anyString(), isNull());
    }

    @Test
    @DisplayName("Download de arquivo registra DOWNLOAD com o id do arquivo")
    void download_registra() {
        executar("GET", "/api/arquivos/42/download", 200);

        verify(auditoriaService).registrarIndependente(eq("DOWNLOAD"), eq("ARQUIVO_BRUTO"), eq(42L), anyString(), isNull());
    }

    @Test
    @DisplayName("Importacao pela API registra API_ACCESS")
    void api_registra() {
        executar("POST", "/api/ingestao/conjuntos/3/importar", 200);

        verify(auditoriaService).registrarIndependente(eq("API_ACCESS"), eq("CONJUNTO_DADOS"), eq(3L), anyString(), isNull());
    }

    @Test
    @DisplayName("Execucao do pipeline registra EXECUCAO com autor padrao AIRFLOW e o dagRunId")
    void execucao_registraComAutorAirflow() {
        executar("POST", "/api/pipeline/executar", 200, "dagRunId", "run-1");

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrarIndependente(
                eq("EXECUCAO"), eq("EXECUCAO_PIPELINE"), isNull(), json.capture(), eq("AIRFLOW"));
        org.junit.jupiter.api.Assertions.assertTrue(json.getValue().contains("run-1"));
    }

    @Test
    @DisplayName("Reexecucao de etapa e processamento de arquivo tambem registram EXECUCAO")
    void reexecucaoEProcessar_registram() {
        executar("POST", "/api/pipeline/execucoes/run-1/etapas/VALIDACAO/reexecutar", 200);
        executar("POST", "/api/arquivos/9/processar", 200);

        verify(auditoriaService, times(2)).registrarIndependente(
                eq("EXECUCAO"), anyString(), any(), anyString(), eq("AIRFLOW"));
    }

    @Test
    @DisplayName("Respostas de erro (>= 400) nao geram registro")
    void erro_naoRegistra() {
        executar("POST", "/api/arquivos/brutos/upload", 400, "conId", "7");
        executar("GET", "/api/arquivos/42/download", 404);

        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("Rotas fora da lista (consultas comuns) nao geram registro")
    void rotaComum_naoRegistra() {
        executar("GET", "/api/v1/catalogo", 200);
        executar("GET", "/api/pipeline/execucoes", 200);

        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("Falha ao gravar a auditoria nao derruba a requisicao")
    void falhaNaAuditoria_naoPropaga() {
        doThrow(new RuntimeException("banco fora")).when(auditoriaService)
                .registrarIndependente(anyString(), anyString(), any(), anyString(), any());

        executar("GET", "/api/arquivos/42/download", 200);
    }
}
