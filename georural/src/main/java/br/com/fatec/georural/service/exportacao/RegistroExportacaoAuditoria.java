package br.com.fatec.georural.service.exportacao;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class RegistroExportacaoAuditoria {

    private final JdbcTemplate jdbc;

    public void registrar(ConsultaExportacaoService.Versao v, String indicador,
                          FormatoExportacao formato, boolean geometria, long linhas) {
        String dados = "{"
                + "\"formato\":\"" + formato + "\","
                + "\"indicador\":" + (indicador == null || indicador.isBlank()
                        ? "null" : "\"" + indicador.trim().toUpperCase() + "\"") + ","
                + "\"linhas\":" + linhas + ","
                + "\"com_geometria\":" + geometria + ","
                + "\"versao_numero\":" + v.numero() + ","
                + "\"competencia\":" + (v.competencia() == null ? "null" : "\"" + v.competencia() + "\"")
                + "}";
        try {
            jdbc.update("BEGIN PKG_AUDIT.registrar('EXPORT', 'VERSAO', ?, ?); END;", v.id(), dados);
        } catch (RuntimeException e) {
            log.error("Falha ao registrar o EXPORT da versao {} na auditoria: {}", v.id(), e.getMessage());
        }
    }
}
