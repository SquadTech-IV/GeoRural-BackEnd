package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.dto.response.pipeline.ResumoOperacaoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResumoOperacaoService {

    private static final int DIAS_PADRAO = 7;
    private static final int DIAS_MAXIMO = 365;

    // versao devolvida fica gravada na auditoria e nao conta como esperando decisao
    private static final String DEVOLVIDA =
            " EXISTS (SELECT 1 FROM AUDITORIA a "
                    + "          WHERE a.aud_entidade_afetada = 'VERSAO' "
                    + "            AND a.aud_registro_id = v.ver_id "
                    + "            AND a.aud_dados_novos LIKE '%\"acao\":\"REPROVACAO\"%')";

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public ResumoOperacaoResponse montar(Long conjuntoId, int dias) {
        int periodo = Math.min(dias <= 0 ? DIAS_PADRAO : dias, DIAS_MAXIMO);
        LocalDateTime desde = LocalDateTime.now().minusDays(periodo);

        Map<String, Object> rodadas = rodadasDoPeriodo(conjuntoId, desde);
        Long duracaoMedia = numero(rodadas.get("DURACAO_MEDIA"));

        return new ResumoOperacaoResponse(
                periodo,
                desde,
                conjuntosPublicados(conjuntoId),
                valor(rodadas.get("RODADAS")),
                valor(rodadas.get("AUTOMATICAS")),
                valor(rodadas.get("RODADAS")) - valor(rodadas.get("AUTOMATICAS")),
                valor(rodadas.get("FALHADAS")),
                data(rodadas.get("FALHA_MAIS_ANTIGA")),
                versoesAguardando(conjuntoId),
                quarentenaPendente(conjuntoId),
                duracaoMedia,
                rotularDuracao(duracaoMedia));
    }

    // conjuntos que ja tem versao vigente
    private long conjuntosPublicados(Long conjuntoId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM CONJUNTO_DADOS WHERE con_ver_vigente_id IS NOT NULL");
        List<Object> parametros = new ArrayList<>();

        if (conjuntoId != null) {
            sql.append(" AND con_id = ?");
            parametros.add(conjuntoId);
        }

        Long total = jdbc.queryForObject(sql.toString(), Long.class, parametros.toArray());
        return total == null ? 0 : total;
    }

    // uma rodada por dag_run_id; a media ignora as que falharam para nao distorcer
    private Map<String, Object> rodadasDoPeriodo(Long conjuntoId, LocalDateTime desde) {
        List<Object> parametros = new ArrayList<>();
        parametros.add(Timestamp.valueOf(desde));

        String filtroConjunto = "";
        if (conjuntoId != null) {
            filtroConjunto = " AND e.con_id = ?";
            parametros.add(conjuntoId);
        }

        String sql = "SELECT COUNT(*) AS rodadas, "
                + "       NVL(SUM(r.automatica), 0) AS automaticas, "
                + "       NVL(SUM(r.falhou), 0) AS falhadas, "
                + "       MIN(CASE WHEN r.falhou = 1 THEN r.inicio END) AS falha_mais_antiga, "
                + "       ROUND(AVG(CASE WHEN r.falhou = 0 THEN r.duracao END)) AS duracao_media "
                + "  FROM (SELECT CASE WHEN e.exe_dag_run_id LIKE 'portal-%' THEN 0 ELSE 1 END AS automatica, "
                + "               MAX(CASE WHEN e.exe_status = 'FALHOU' THEN 1 ELSE 0 END) AS falhou, "
                + "               MIN(e.exe_inicio) AS inicio, "
                + "               SUM(NVL(e.exe_duracao_seg, 0)) AS duracao "
                + "          FROM EXECUCAO_PIPELINE e "
                + "         WHERE e.exe_dag_run_id IS NOT NULL "
                + "           AND e.exe_inicio >= ?"
                + filtroConjunto
                + "         GROUP BY e.exe_dag_run_id) r";

        return jdbc.queryForMap(sql, parametros.toArray());
    }

    // versoes em rascunho que ninguem devolveu: e o mesmo criterio da fila da conferencia
    private long versoesAguardando(Long conjuntoId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM VERSAO v WHERE v.ver_situacao = 'RASCUNHO' AND NOT" + DEVOLVIDA);
        List<Object> parametros = new ArrayList<>();

        if (conjuntoId != null) {
            sql.append(" AND v.con_id = ?");
            parametros.add(conjuntoId);
        }

        Long total = jdbc.queryForObject(sql.toString(), Long.class, parametros.toArray());
        return total == null ? 0 : total;
    }

    private long quarentenaPendente(Long conjuntoId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) "
                        + "  FROM QUARENTENA q "
                        + "  JOIN VALIDACAO v ON v.val_id = q.val_id "
                        + "  LEFT JOIN EXECUCAO_PIPELINE e ON e.exe_id = v.exe_id "
                        + " WHERE q.qua_situacao = 'PENDENTE'");
        List<Object> parametros = new ArrayList<>();

        if (conjuntoId != null) {
            sql.append(" AND e.con_id = ?");
            parametros.add(conjuntoId);
        }

        Long total = jdbc.queryForObject(sql.toString(), Long.class, parametros.toArray());
        return total == null ? 0 : total;
    }

    // "4m 12s" pronto, para a tela nao ter que formatar de novo
    private static String rotularDuracao(Long segundos) {
        if (segundos == null || segundos <= 0) {
            return "—";
        }

        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        long resto = segundos % 60;

        if (horas > 0) {
            return horas + "h " + String.format("%02d", minutos) + "m";
        }
        if (minutos > 0) {
            return minutos + "m " + String.format("%02d", resto) + "s";
        }
        return resto + "s";
    }

    private static long valor(Object bruto) {
        return bruto == null ? 0 : ((Number) bruto).longValue();
    }

    private static Long numero(Object bruto) {
        return bruto == null ? null : ((Number) bruto).longValue();
    }

    private static LocalDateTime data(Object bruto) {
        return bruto instanceof Timestamp t ? t.toLocalDateTime() : null;
    }
}
