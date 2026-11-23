package br.com.fatec.georural.config;

import br.com.fatec.georural.service.auditoria.AuditoriaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * US#20.01 - Registra na AUDITORIA as acoes que nao passam por trigger:
 * envio de arquivo, download, chamada a API de ingestao e disparo de execucao do pipeline.
 * (Regras e publicacoes ja sao auditadas por trigger; exportacao por RegistroExportacaoAuditoria.)
 *
 * So registra respostas de sucesso (status < 400) e nunca derruba a requisicao se a gravacao falhar.
 * Responsavel = usuario do token; sem usuario, execucoes ficam como "AIRFLOW" e o resto como "sistema".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditoriaAcaoInterceptor implements HandlerInterceptor {

    static final String AUTOR_AIRFLOW = "AIRFLOW";

    private static final String UPLOAD = "/api/arquivos/brutos/upload";
    private static final Pattern DOWNLOAD = Pattern.compile("^/api/arquivos/(\\d+)/download$");
    private static final Pattern PROCESSAR = Pattern.compile("^/api/arquivos/(\\d+)/processar$");
    private static final Pattern IMPORTAR = Pattern.compile("^/api/ingestao/conjuntos/(\\d+)/importar$");
    private static final Pattern EXECUCAO = Pattern.compile(
            "^/api/pipeline/(executar|lote|etapas/[^/]+|execucoes/[^/]+/etapas/[^/]+/reexecutar)$");

    private final AuditoriaService auditoriaService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (ex != null || response.getStatus() >= 400) {
            return;
        }
        try {
            registrar(request);
        } catch (Exception e) {
            log.error("Falha ao registrar auditoria de {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
        }
    }

    private void registrar(HttpServletRequest request) throws JsonProcessingException {
        String metodo = request.getMethod();
        String caminho = request.getRequestURI();

        if ("GET".equals(metodo)) {
            Matcher m = DOWNLOAD.matcher(caminho);
            if (m.matches()) {
                auditoriaService.registrarIndependente("DOWNLOAD", "ARQUIVO_BRUTO",
                        Long.valueOf(m.group(1)), json(Map.of("caminho", caminho)), null);
            }
            return;
        }
        if (!"POST".equals(metodo)) {
            return;
        }

        if (UPLOAD.equals(caminho)) {
            Long conId = numero(request.getParameter("conId"));
            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("caminho", caminho);
            dados.put("conjuntoId", conId);
            auditoriaService.registrarIndependente("UPLOAD", "CONJUNTO_DADOS", conId, json(dados), null);
            return;
        }

        Matcher importar = IMPORTAR.matcher(caminho);
        if (importar.matches()) {
            auditoriaService.registrarIndependente("API_ACCESS", "CONJUNTO_DADOS",
                    Long.valueOf(importar.group(1)), json(Map.of("caminho", caminho)), null);
            return;
        }

        Matcher processar = PROCESSAR.matcher(caminho);
        if (processar.matches()) {
            auditoriaService.registrarIndependente("EXECUCAO", "ARQUIVO_BRUTO",
                    Long.valueOf(processar.group(1)), json(Map.of("caminho", caminho)), AUTOR_AIRFLOW);
            return;
        }

        if (EXECUCAO.matcher(caminho).matches()) {
            Map<String, Object> dados = new LinkedHashMap<>();
            dados.put("caminho", caminho);
            String dagRunId = request.getParameter("dagRunId");
            if (dagRunId != null && !dagRunId.isBlank()) {
                dados.put("dagRunId", dagRunId);
            }
            auditoriaService.registrarIndependente("EXECUCAO", "EXECUCAO_PIPELINE",
                    null, json(dados), AUTOR_AIRFLOW);
        }
    }

    private String json(Map<String, ?> dados) throws JsonProcessingException {
        return objectMapper.writeValueAsString(dados);
    }

    private static Long numero(String valor) {
        try {
            return valor == null ? null : Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
