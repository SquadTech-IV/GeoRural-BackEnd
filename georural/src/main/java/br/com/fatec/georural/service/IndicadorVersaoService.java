package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.versao.IndicadoresDaVersaoResponse;
import br.com.fatec.georural.dto.response.versao.IndicadoresDaVersaoResponse.IndicadorContagem;
import br.com.fatec.georural.dto.response.versao.IndicadoresDaVersaoResponse.PorSituacao;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IndicadorVersaoService {

    private final JdbcTemplate jdbc;

    // contagem por situacao, desta versao e da que esta vigente, para comparar antes de publicar
    @Transactional(readOnly = true)
    public IndicadoresDaVersaoResponse daVersao(Long versaoId) {
        Map<String, Object> versao = jdbc.queryForMap(
                "SELECT ver_id, ver_numero, ver_situacao, con_id FROM VERSAO WHERE ver_id = ?",
                versaoId);

        Long conjuntoId = numero(versao.get("CON_ID"));
        Map<String, Object> vigente = vigenteDoConjunto(conjuntoId, versaoId);
        Long comparadaId = vigente == null ? null : numero(vigente.get("VER_ID"));

        Map<String, Contagens> desta = contar(versaoId);
        Map<String, Contagens> daVigente = comparadaId == null ? Map.of() : contar(comparadaId);

        List<IndicadorContagem> indicadores = new ArrayList<>();
        for (Map.Entry<String, Contagens> entrada : desta.entrySet()) {
            Contagens atual = entrada.getValue();
            Contagens antes = daVigente.get(entrada.getKey());

            List<PorSituacao> situacoes = new ArrayList<>();
            for (Map.Entry<String, Long> s : atual.porSituacao.entrySet()) {
                long antiga = antes == null ? 0 : antes.porSituacao.getOrDefault(s.getKey(), 0L);
                situacoes.add(new PorSituacao(s.getKey(), s.getValue(), antiga));
            }

            // situacao que existia antes e sumiu nesta versao
            if (antes != null) {
                for (Map.Entry<String, Long> s : antes.porSituacao.entrySet()) {
                    if (!atual.porSituacao.containsKey(s.getKey())) {
                        situacoes.add(new PorSituacao(s.getKey(), 0, s.getValue()));
                    }
                }
            }

            indicadores.add(new IndicadorContagem(
                    entrada.getKey(), atual.nome, atual.total(),
                    antes == null ? 0 : antes.total(), situacoes));
        }

        return new IndicadoresDaVersaoResponse(
                versaoId,
                numero(versao.get("VER_NUMERO")),
                String.valueOf(versao.get("VER_SITUACAO")),
                comparadaId,
                vigente == null ? null : numero(vigente.get("VER_NUMERO")),
                indicadores);
    }

    private Map<String, Object> vigenteDoConjunto(Long conjuntoId, Long excetoVersaoId) {
        List<Map<String, Object>> achados = jdbc.queryForList(
                "SELECT ver_id, ver_numero FROM VERSAO "
                        + " WHERE con_id = ? AND ver_situacao = 'VIGENTE' AND ver_id <> ? "
                        + " ORDER BY ver_numero DESC "
                        + "OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY",
                conjuntoId, excetoVersaoId);
        return achados.isEmpty() ? null : achados.get(0);
    }

    private Map<String, Contagens> contar(Long versaoId) {
        Map<String, Contagens> porIndicador = new LinkedHashMap<>();

        jdbc.query("SELECT i.ind_sigla, i.ind_nome, "
                        + "       NVL(r.rin_situacao, 'SEM_SITUACAO') AS situacao, "
                        + "       COUNT(*) AS quantos "
                        + "  FROM RESULTADO_INDICADOR r "
                        + "  JOIN INDICADOR i ON i.ind_id = r.ind_id "
                        + " WHERE r.ver_id = ? "
                        + " GROUP BY i.ind_sigla, i.ind_nome, r.rin_situacao "
                        + " ORDER BY i.ind_sigla, situacao",
                rs -> {
                    String sigla = rs.getString("ind_sigla");
                    String nome = rs.getString("ind_nome");
                    Contagens c = porIndicador.computeIfAbsent(sigla, k -> new Contagens(nome));
                    c.porSituacao.put(rs.getString("situacao"), rs.getLong("quantos"));
                }, versaoId);

        return porIndicador;
    }

    private static Long numero(Object valor) {
        return valor == null ? null : ((Number) valor).longValue();
    }

    private static final class Contagens {
        private final String nome;
        private final Map<String, Long> porSituacao = new LinkedHashMap<>();

        private Contagens(String nome) {
            this.nome = nome;
        }

        private long total() {
            return porSituacao.values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
