package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.dto.response.indicador.BarraHistogramaResponse;
import br.com.fatec.georural.dto.response.indicador.ContagemSituacaoResponse;
import br.com.fatec.georural.dto.response.indicador.SeriesIndicadorResponse;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SerieIndicadorService {

    private static final int FAIXAS_PADRAO = 5;
    private static final int MINIMO_DE_FAIXAS = 2;
    private static final int MAXIMO_DE_FAIXAS = 9;

    private final JdbcTemplate jdbc;

    public SeriesIndicadorResponse montar(FiltroSerie f) {
        int qtd = validarQtdFaixas(f.qtdFaixas());
        Indicador ind = buscarIndicador(f.indicador());

        Filtro filtro = montarFiltro(f, ind.id());
        List<String> avisos = new ArrayList<>();

        long[] totais = contar(filtro);
        if (totais[0] == 0) {
            return vazio(ind, qtd, avisos);
        }

        Escala escala = resolverEscala(ind, filtro, avisos);
        List<ContagemSituacaoResponse> composicao = composicao(filtro, totais[0]);
        Histograma h = histograma(filtro, escala, qtd, totais[0]);

        if (h.abaixo() > 0 || h.acima() > 0) {
            avisos.add(h.abaixo() + h.acima() + " resultado(s) caem fora da escala "
                    + escala.min() + " a " + escala.max() + " e nao entram em nenhuma barra.");
        }
        return new SeriesIndicadorResponse(ind.sigla(), ind.nome(), ind.unidade(), ind.sentido(),
                totais[0], totais[1], escala.min(), escala.max(), escala.origem(),
                composicao, h.barras(), h.abaixo(), h.acima(), avisos);
    }

    private int validarQtdFaixas(Integer pedido) {
        int qtd = pedido == null ? FAIXAS_PADRAO : pedido;
        if (qtd < MINIMO_DE_FAIXAS || qtd > MAXIMO_DE_FAIXAS) {
            throw new RequisicaoInvalidaException(
                    "qtdFaixas deve estar entre " + MINIMO_DE_FAIXAS + " e " + MAXIMO_DE_FAIXAS
                    + ": recebido " + qtd + ".");
        }
        return qtd;
    }

    // busca o indicador pela sigla e ja traz o sentido e a escala da V6
    private Indicador buscarIndicador(String sigla) {
        if (sigla == null || sigla.isBlank()) {
            throw new RequisicaoInvalidaException(
                    "O parametro indicador e obrigatorio: histograma de indicadores com unidades "
                    + "diferentes no mesmo eixo nao significa nada.");
        }
        List<Map<String, Object>> r = jdbc.queryForList("""
                SELECT ind_id, ind_sigla, ind_nome, ind_unidade, ind_sentido,
                       ind_escala_min, ind_escala_max
                  FROM INDICADOR WHERE UPPER(ind_sigla) = ?
                """, sigla.trim().toUpperCase());
        if (r.isEmpty()) {
            throw new RecursoNaoEncontradoException("Indicador " + sigla + " nao cadastrado.");
        }
        Map<String, Object> m = r.get(0);
        return new Indicador(((Number) m.get("IND_ID")).longValue(),
                (String) m.get("IND_SIGLA"), (String) m.get("IND_NOME"),
                (String) m.get("IND_UNIDADE"), (String) m.get("IND_SENTIDO"),
                (BigDecimal) m.get("IND_ESCALA_MIN"), (BigDecimal) m.get("IND_ESCALA_MAX"));
    }

    // monta o WHERE uma vez so: os tres SELECTs abaixo usam o mesmo recorte
    private Filtro montarFiltro(FiltroSerie f, Long indId) {
        StringBuilder w = new StringBuilder(" WHERE r.ind_id = ?");
        List<Object> p = new ArrayList<>();
        p.add(indId);

        if (f.versaoId() != null)        { w.append(" AND r.ver_id = ?"); p.add(f.versaoId()); }
        if (texto(f.competencia()))      { w.append(" AND v.ver_competencia = ?"); p.add(f.competencia().trim()); }
        if (f.municipioId() != null)     { w.append(" AND i.mun_id = ?"); p.add(f.municipioId()); }
        if (texto(f.municipio()))        { w.append(" AND UPPER(m.mun_nome) LIKE ?");
                                           p.add("%" + f.municipio().trim().toUpperCase() + "%"); }
        if (texto(f.codigoCar()))        { w.append(" AND UPPER(i.imo_codigo_car) LIKE ?");
                                           p.add("%" + f.codigoCar().trim().toUpperCase() + "%"); }
        if (texto(f.situacao()))         { w.append(" AND UPPER(r.rin_situacao) = ?");
                                           p.add(f.situacao().trim().toUpperCase()); }
        if (f.vigente() != null)         { w.append(" AND r.rin_vigente = ?");
                                           p.add(Boolean.TRUE.equals(f.vigente()) ? "S" : "N"); }
        if (f.valorMinimo() != null)     { w.append(" AND " + VALOR + " >= ?"); p.add(f.valorMinimo()); }
        if (f.valorMaximo() != null)     { w.append(" AND " + VALOR + " <= ?"); p.add(f.valorMaximo()); }

        return new Filtro(w.toString(), p.toArray());
    }

    // o IFC nao tem percentual: o valor dele esta em rin_valor_absoluto
    private static final String VALOR = "COALESCE(r.rin_valor_percentual, r.rin_valor_absoluto)";

    private static final String DE = """
              FROM RESULTADO_INDICADOR r
              JOIN IMOVEL_RURAL i ON i.imo_id = r.imo_id
              JOIN VERSAO v       ON v.ver_id = r.ver_id
              LEFT JOIN MUNICIPIO m ON m.mun_id = i.mun_id
            """;

    private long[] contar(Filtro f) {
        Map<String, Object> r = jdbc.queryForMap(
                "SELECT COUNT(*) AS n, COUNT(DISTINCT r.imo_id) AS imoveis " + DE + f.where(), f.params());
        return new long[]{ ((Number) r.get("N")).longValue(), ((Number) r.get("IMOVEIS")).longValue() };
    }

    private Escala resolverEscala(Indicador ind, Filtro f, List<String> avisos) {
        if (ind.escalaMin() != null && ind.escalaMax() != null) {
            return new Escala(ind.escalaMin(), ind.escalaMax(), "INDICADOR");
        }
        // escala nula e normal, nao defeito: focos/1000ha nao tem teto
        Map<String, Object> r = jdbc.queryForMap(
                "SELECT MIN(" + VALOR + ") AS mn, MAX(" + VALOR + ") AS mx " + DE + f.where(), f.params());
        BigDecimal mn = (BigDecimal) r.get("MN");
        BigDecimal mx = (BigDecimal) r.get("MX");
        if (mn == null || mx == null || mn.compareTo(mx) == 0) {
            mn = mn == null ? BigDecimal.ZERO : mn;
            mx = mx == null ? BigDecimal.ONE : mx.add(BigDecimal.ONE);
        }
        avisos.add("O indicador " + ind.sigla() + " nao tem escala cadastrada: as faixas sairam do minimo "
                + "e do maximo deste recorte e mudam de lugar quando os dados mudam.");
        return new Escala(mn, mx, "DADOS");
    }

    private List<ContagemSituacaoResponse> composicao(Filtro f, long total) {
        return jdbc.query("SELECT r.rin_situacao AS situacao, COUNT(*) AS n " + DE + f.where()
                        + " GROUP BY r.rin_situacao ORDER BY COUNT(*) DESC",
                (rs, i) -> {
                    long n = rs.getLong("N");
                    return new ContagemSituacaoResponse(rs.getString("SITUACAO"), n, percentual(n, total));
                }, f.params());
    }

    // WIDTH_BUCKET faz o histograma no banco: as 274 mil linhas nunca saem de la.
    // Balde 0 = abaixo da escala; balde qtd+1 = acima.
    // O CASE existe porque o WIDTH_BUCKET manda o valor IGUAL ao maximo para o balde de
    // estouro, e a ultima faixa e [de, ate] fechada: 100% de IRL tem que cair nela.
    private Histograma histograma(Filtro f, Escala e, int qtd, long total) {
        Map<Integer, Long> porBalde = new java.util.HashMap<>();
        String balde = "CASE WHEN " + VALOR + " = ? THEN ? ELSE WIDTH_BUCKET(" + VALOR + ", ?, ?, ?) END";
        jdbc.query("SELECT " + balde + " AS balde, COUNT(*) AS n " + DE + f.where()
                        + " GROUP BY " + balde,
                rs -> { porBalde.put(rs.getInt("BALDE"), rs.getLong("N")); },
                parametrosDoBalde(f, e, qtd));

        BigDecimal largura = e.max().subtract(e.min())
                .divide(BigDecimal.valueOf(qtd), 6, RoundingMode.HALF_UP);
        List<BarraHistogramaResponse> barras = new ArrayList<>(qtd);
        for (int b = 1; b <= qtd; b++) {
            BigDecimal de = e.min().add(largura.multiply(BigDecimal.valueOf(b - 1)));
            BigDecimal ate = b == qtd ? e.max() : e.min().add(largura.multiply(BigDecimal.valueOf(b)));
            long n = porBalde.getOrDefault(b, 0L);
            barras.add(new BarraHistogramaResponse(b, arredondar(de), arredondar(ate),
                    arredondar(de) + " a " + arredondar(ate), n, percentual(n, total)));
        }
        return new Histograma(barras, porBalde.getOrDefault(0, 0L), porBalde.getOrDefault(qtd + 1, 0L));
    }

    // o balde aparece no SELECT e no GROUP BY, entao os 5 parametros dele vao duas vezes,
    // com os filtros no meio
    private Object[] parametrosDoBalde(Filtro f, Escala e, int qtd) {
        List<Object> todos = new ArrayList<>();
        todos.addAll(List.of(e.max(), qtd, e.min(), e.max(), qtd));
        todos.addAll(List.of(f.params()));
        todos.addAll(List.of(e.max(), qtd, e.min(), e.max(), qtd));
        return todos.toArray();
    }

    private SeriesIndicadorResponse vazio(Indicador ind, int qtd, List<String> avisos) {
        avisos.add("Nenhum resultado para este recorte.");
        return new SeriesIndicadorResponse(ind.sigla(), ind.nome(), ind.unidade(), ind.sentido(),
                0, 0, ind.escalaMin(), ind.escalaMax(),
                ind.escalaMin() == null ? "SEM_DADOS" : "INDICADOR",
                List.of(), List.of(), 0, 0, avisos);
    }

    private BigDecimal percentual(long parte, long total) {
        if (total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(parte).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal arredondar(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }

    private boolean texto(String s) { return s != null && !s.isBlank(); }

    private record Indicador(Long id, String sigla, String nome, String unidade, String sentido,
                             BigDecimal escalaMin, BigDecimal escalaMax) {}
    private record Filtro(String where, Object[] params) {}
    private record Escala(BigDecimal min, BigDecimal max, String origem) {}
    private record Histograma(List<BarraHistogramaResponse> barras, long abaixo, long acima) {}
}
