package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.dto.response.pipeline.ExecucaoResumoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExecucaoConsultaService {

    private static final int LIMITE_PADRAO = 50;
    private static final int LIMITE_MAXIMO = 500;

    private final JdbcTemplate jdbc;

    // rodadas do pipeline, uma linha por dag_run_id
    // filtro: TODAS (padrao), AGUARDANDO, REPROVADAS ou FALHADAS
    @Transactional(readOnly = true)
    public List<ExecucaoResumoResponse> listar(Long conjuntoId, String filtro, int limite,
                                               Integer dias, LocalDate de, LocalDate ate) {
        int teto = Math.min(limite <= 0 ? LIMITE_PADRAO : limite, LIMITE_MAXIMO);
        List<Object> parametros = new ArrayList<>();

        // de/ate mandam; dias e o atalho que a tela usa nos botoes de periodo
        LocalDateTime inicio = de != null ? de.atStartOfDay()
                : (dias != null && dias > 0 ? LocalDateTime.now().minusDays(dias) : null);

        // ate e inclusivo: o dia escolhido conta inteiro
        LocalDateTime fim = ate != null ? ate.plusDays(1).atStartOfDay() : null;

        String dentro = " WHERE e.exe_dag_run_id IS NOT NULL";
        if (conjuntoId != null) {
            dentro += " AND e.con_id = ?";
            parametros.add(conjuntoId);
        }

        // versao devolvida fica gravada na auditoria e nao volta para a fila
        String devolvida = " EXISTS (SELECT 1 FROM AUDITORIA a "
                + "                   WHERE a.aud_entidade_afetada = 'VERSAO' "
                + "                     AND a.aud_registro_id = v.ver_id "
                + "                     AND a.aud_dados_novos LIKE '%\"acao\":\"REPROVACAO\"%')";

        List<String> condicoes = new ArrayList<>();
        if ("AGUARDANDO".equalsIgnoreCase(filtro)) {
            condicoes.add("v.ver_situacao = 'RASCUNHO' AND NOT" + devolvida);
        } else if ("REPROVADAS".equalsIgnoreCase(filtro)) {
            condicoes.add(devolvida);
        } else if ("FALHADAS".equalsIgnoreCase(filtro)) {
            condicoes.add("r.falhadas > 0");
        }

        // o periodo olha quando a rodada comecou, nao cada etapa
        if (inicio != null) {
            condicoes.add("r.inicio >= ?");
            parametros.add(Timestamp.valueOf(inicio));
        }
        if (fim != null) {
            condicoes.add("r.inicio < ?");
            parametros.add(Timestamp.valueOf(fim));
        }

        String fora = condicoes.isEmpty() ? "" : " WHERE " + String.join(" AND ", condicoes);

        String sql = "SELECT r.dag_run_id, r.con_id, r.con_nome, r.arq_id, r.arq_nome, r.canal, "
                + "       r.com_usuario, r.etapas, r.falhadas, r.ressalvas, r.recebidas, r.rejeitadas, "
                + "       r.etapa_falha, r.msg_falha, r.inicio, r.fim, r.ver_id, "
                + "       v.ver_numero, v.ver_situacao, v.ver_exe_id "
                + "  FROM (SELECT e.exe_dag_run_id AS dag_run_id, "
                + "               MIN(e.con_id) AS con_id, "
                + "               MIN(c.con_nome) AS con_nome, "
                + "               MIN(e.arq_bruto_id) AS arq_id, "
                + "               MIN(a.arq_nome) AS arq_nome, "
                + "               MIN(a.arq_canal_entrada) AS canal, "
                + "               COUNT(e.usu_id) AS com_usuario, "
                + "               COUNT(*) AS etapas, "
                + "               SUM(CASE WHEN e.exe_status = 'FALHOU' THEN 1 ELSE 0 END) AS falhadas, "
                + "               SUM(CASE WHEN e.exe_status = 'COM_RESSALVA' THEN 1 ELSE 0 END) AS ressalvas, "
                + "               MAX(NVL(e.exe_registros_recebidos, 0)) AS recebidas, "
                + "               SUM(NVL(e.exe_registros_rejeitados, 0)) AS rejeitadas, "
                + "               MAX(CASE WHEN e.exe_status = 'FALHOU' THEN e.exe_etapa END) AS etapa_falha, "
                + "               MAX(CASE WHEN e.exe_status = 'FALHOU' THEN e.exe_mensagem END) AS msg_falha, "
                + "               MIN(e.exe_inicio) AS inicio, "
                + "               MAX(e.exe_fim) AS fim, "
                + "               MAX(e.exe_ver_gerada_id) AS ver_id "
                + "          FROM EXECUCAO_PIPELINE e "
                + "          LEFT JOIN CONJUNTO_DADOS c ON c.con_id = e.con_id "
                + "          LEFT JOIN ARQUIVO_BRUTO a ON a.arq_bruto_id = e.arq_bruto_id "
                + dentro
                + "         GROUP BY e.exe_dag_run_id) r "
                + "  LEFT JOIN VERSAO v ON v.ver_id = r.ver_id "
                + fora
                + " ORDER BY r.inicio DESC "
                + "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";

        parametros.add(teto);

        return jdbc.query(sql, (rs, linha) -> {
            long falhadas = rs.getLong("falhadas");
            long ressalvas = rs.getLong("ressalvas");
            long rejeitadas = rs.getLong("rejeitadas");

            String situacao = falhadas > 0 ? "FALHOU"
                    : (ressalvas > 0 || rejeitadas > 0) ? "COM_RESSALVA" : "CONCLUIDA";

            // rodada disparada pela tela nasce com dagRunId "portal-"; o resto e automatico
            String dagRunId = rs.getString("dag_run_id");
            boolean doPortal = dagRunId != null && dagRunId.startsWith("portal-");
            boolean automatica = !doPortal && rs.getLong("com_usuario") == 0;

            Long versaoId = (Long) rs.getObject("ver_id", Long.class);
            Long versaoNumero = (Long) rs.getObject("ver_numero", Long.class);
            Long execucaoId = (Long) rs.getObject("ver_exe_id", Long.class);

            return new ExecucaoResumoResponse(
                    dagRunId,
                    (Long) rs.getObject("con_id", Long.class),
                    rs.getString("con_nome"),
                    (Long) rs.getObject("arq_id", Long.class),
                    rs.getString("arq_nome"),
                    rs.getString("canal"),
                    automatica,
                    situacao,
                    rs.getString("etapa_falha"),
                    rs.getString("msg_falha"),
                    rs.getInt("etapas"),
                    rs.getLong("recebidas"),
                    rejeitadas,
                    paraData(rs.getTimestamp("inicio")),
                    paraData(rs.getTimestamp("fim")),
                    versaoId,
                    versaoNumero,
                    rs.getString("ver_situacao"),
                    execucaoId);
        }, parametros.toArray());
    }

    private static LocalDateTime paraData(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
