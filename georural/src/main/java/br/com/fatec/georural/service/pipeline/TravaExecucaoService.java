package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.exception.ConflitoException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TravaExecucaoService {

    // execucao parada ha mais que isso e considerada abandonada e nao trava mais
    private static final int HORAS_ATE_ABANDONAR = 2;

    private final JdbcTemplate jdbc;

    // recusa comecar quando ja existe outra rodada viva no mesmo conjunto
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void conferir(Long conjuntoId, String dagRunId) {
        if (conjuntoId == null) {
            return;
        }

        List<Map<String, Object>> vivas = jdbc.queryForList(
                "SELECT e.exe_dag_run_id AS dag, MIN(e.exe_etapa) AS etapa, MIN(e.exe_inicio) AS inicio "
                        + "  FROM EXECUCAO_PIPELINE e "
                        + " WHERE e.con_id = ? "
                        + "   AND e.exe_status = 'EM_ANDAMENTO' "
                        + "   AND NVL(e.exe_dag_run_id, 'x') <> NVL(?, 'y') "
                        + "   AND e.exe_inicio > SYSTIMESTAMP - INTERVAL '" + HORAS_ATE_ABANDONAR + "' HOUR "
                        + " GROUP BY e.exe_dag_run_id "
                        + " ORDER BY MIN(e.exe_inicio) "
                        + "OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY",
                conjuntoId, dagRunId);

        if (vivas.isEmpty()) {
            return;
        }

        Map<String, Object> viva = vivas.get(0);
        throw new ConflitoException(
                "Ja existe um processamento em andamento neste conjunto (execucao "
                        + viva.get("DAG") + ", parada na etapa " + viva.get("ETAPA")
                        + " desde " + viva.get("INICIO")
                        + "). Espere terminar antes de comecar outro.");
    }
}
