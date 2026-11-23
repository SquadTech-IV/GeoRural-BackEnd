package br.com.fatec.georural.service.monitoramento;

import br.com.fatec.georural.dto.response.monitoramento.ErrosResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// consulta dos erros registrados, com filtros e busca por texto
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultaErrosService {

    private static final int LIMITE_PADRAO = 50;
    private static final int LIMITE_MAXIMO = 500;

    private final JdbcTemplate jdbc;

    public ErrosResponse consultar(LocalDate de, LocalDate ate, Integer statusMinimo,
                                   Integer statusMaximo, String busca, Integer limite,
                                   Integer deslocamento) {

        int quantos = limite == null || limite <= 0 ? LIMITE_PADRAO : Math.min(limite, LIMITE_MAXIMO);
        int pular = deslocamento == null || deslocamento < 0 ? 0 : deslocamento;

        List<String> condicoes = new ArrayList<>();
        List<Object> valores = new ArrayList<>();

        if (de != null) {
            condicoes.add("e.err_data_hora >= ?");
            valores.add(Timestamp.valueOf(de.atStartOfDay()));
        }
        if (ate != null) {
            // dia cheio: ate o inicio do dia seguinte
            condicoes.add("e.err_data_hora < ?");
            valores.add(Timestamp.valueOf(ate.plusDays(1).atStartOfDay()));
        }
        if (statusMinimo != null) {
            condicoes.add("e.err_status >= ?");
            valores.add(statusMinimo);
        }
        if (statusMaximo != null) {
            condicoes.add("e.err_status <= ?");
            valores.add(statusMaximo);
        }
        if (busca != null && !busca.isBlank()) {
            // busca em rota, excecao e mensagem de uma vez; sem acento e sem caixa
            String alvo = "%" + busca.trim().toLowerCase() + "%";
            condicoes.add("(LOWER(e.err_rota) LIKE ? OR LOWER(e.err_excecao) LIKE ? "
                    + "OR LOWER(e.err_mensagem) LIKE ?)");
            valores.add(alvo);
            valores.add(alvo);
            valores.add(alvo);
        }

        String onde = condicoes.isEmpty() ? "" : " WHERE " + String.join(" AND ", condicoes);

        long total = contar(onde, valores);
        List<ErrosResponse.Resumo> porRota = agrupar(onde, valores);
        List<ErrosResponse.Erro> erros = pagina(onde, valores, pular, quantos);

        return new ErrosResponse(LocalDateTime.now(), total, quantos, pular, porRota, erros,
                observacoes());
    }

    private long contar(String onde, List<Object> valores) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM ERRO_API e" + onde,
                Long.class, valores.toArray());
        return total == null ? 0L : total;
    }

    private List<ErrosResponse.Resumo> agrupar(String onde, List<Object> valores) {
        return jdbc.query(
                " SELECT * FROM ( "
              + "   SELECT e.err_metodo AS metodo, e.err_rota AS rota, COUNT(*) AS erros, "
              + "          MAX(e.err_data_hora) AS ultimo "
              + "     FROM ERRO_API e" + onde
              + "    GROUP BY e.err_metodo, e.err_rota "
              + "    ORDER BY COUNT(*) DESC, MAX(e.err_data_hora) DESC "
              + " ) WHERE ROWNUM <= 10 ",
                (rs, linha) -> new ErrosResponse.Resumo(
                        rs.getString("metodo"),
                        rs.getString("rota"),
                        rs.getLong("erros"),
                        rs.getTimestamp("ultimo").toLocalDateTime()),
                valores.toArray());
    }

    private List<ErrosResponse.Erro> pagina(String onde, List<Object> valores,
                                            int pular, int quantos) {
        List<Object> comPaginacao = new ArrayList<>(valores);
        comPaginacao.add(pular);
        comPaginacao.add(quantos);

        return jdbc.query(
                " SELECT e.err_id, e.err_data_hora, e.err_metodo, e.err_rota, e.err_status, "
              + "        e.err_excecao, e.err_mensagem, e.err_pilha, e.err_ip_origem, "
              + "        e.err_correlacao, e.err_duracao_ms "
              + "   FROM ERRO_API e" + onde
              + "  ORDER BY e.err_data_hora DESC, e.err_id DESC "
              + "  OFFSET ? ROWS FETCH NEXT ? ROWS ONLY ",
                (rs, linha) -> montarErro(rs),
                comPaginacao.toArray());
    }

    private List<String> observacoes() {
        List<String> notas = new ArrayList<>();
        notas.add("Sao os erros que esta instancia atendeu; a tabela e limpa conforme "
                + "georural.monitoramento.erros.retencao-dias.");
        notas.add("A rota gravada e o caminho, sem a query string: ela pode levar token ou "
                + "dado pessoal.");
        notas.add("As rotas de /api/monitoramento nao sao registradas, para a tela nao gerar "
                + "erro sobre erro.");
        return notas;
    }

    // o wasNull() tem que vir depois do getLong, por isso a variavel local
    private static ErrosResponse.Erro montarErro(java.sql.ResultSet rs) throws SQLException {
        long duracao = rs.getLong("err_duracao_ms");
        Long duracaoMs = rs.wasNull() ? null : duracao;

        return new ErrosResponse.Erro(
                rs.getLong("err_id"),
                rs.getTimestamp("err_data_hora").toLocalDateTime(),
                rs.getString("err_metodo"),
                rs.getString("err_rota"),
                rs.getInt("err_status"),
                rs.getString("err_excecao"),
                rs.getString("err_mensagem"),
                rs.getString("err_pilha"),
                rs.getString("err_ip_origem"),
                rs.getString("err_correlacao"),
                duracaoMs);
    }
}
