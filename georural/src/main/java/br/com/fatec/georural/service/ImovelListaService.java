package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.imovel.ImovelListaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ImovelListaService {

    private static final int TAMANHO_PADRAO = 200;
    private static final int TAMANHO_MAXIMO = 1000;

    private final JdbcTemplate jdbc;

    // uma consulta so; o IAE vem por janela em vez de uma busca por imovel
    // so entra resultado de versao publicada: rin_vigente = 'S'
    @Transactional(readOnly = true)
    public List<ImovelListaResponse> listar(String municipio, String busca, int pagina, int tamanho) {
        int limite = Math.min(tamanho <= 0 ? TAMANHO_PADRAO : tamanho, TAMANHO_MAXIMO);
        int atual = Math.max(pagina, 0);

        StringBuilder condicoes = new StringBuilder(" WHERE 1 = 1");
        List<Object> parametros = new ArrayList<>();

        if (municipio != null && !municipio.isBlank()) {
            condicoes.append(" AND m.mun_codigo_origem = ?");
            parametros.add(municipio.trim());
        }
        if (busca != null && !busca.isBlank()) {
            condicoes.append(" AND (UPPER(i.imo_codigo_car) LIKE ?"
                    + " OR UPPER(NVL(i.imo_nome, ' ')) LIKE ?)");
            String termo = "%" + busca.trim().toUpperCase() + "%";
            parametros.add(termo);
            parametros.add(termo);
        }

        parametros.add((long) atual * limite);
        parametros.add(limite);

        String sql = "SELECT i.imo_id, i.imo_codigo_car, i.imo_nome, i.imo_area_total, "
                + "m.mun_nome, m.mun_codigo_origem, r.rin_valor_percentual "
                + "FROM IMOVEL_RURAL i "
                + "LEFT JOIN MUNICIPIO m ON m.mun_id = i.mun_id "
                + "LEFT JOIN (SELECT imo_id, rin_valor_percentual, "
                + "                  ROW_NUMBER() OVER (PARTITION BY imo_id "
                + "                      ORDER BY rin_data_calculo DESC) AS ordem "
                + "             FROM RESULTADO_INDICADOR "
                + "            WHERE rin_vigente = 'S' "
                + "              AND ind_id = (SELECT ind_id FROM INDICADOR "
                + "                             WHERE ind_sigla = 'IAE')) r "
                + "  ON r.imo_id = i.imo_id AND r.ordem = 1"
                + condicoes
                + " ORDER BY m.mun_nome NULLS LAST, i.imo_codigo_car "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        return jdbc.query(sql, (rs, linha) -> {
            String nome = rs.getString("imo_nome");
            String car = rs.getString("imo_codigo_car");
            String cidade = rs.getString("mun_nome");
            return new ImovelListaResponse(
                    rs.getLong("imo_id"), car, nome, rotulo(nome, car, cidade), cidade,
                    rs.getString("mun_codigo_origem"),
                    rs.getBigDecimal("imo_area_total"),
                    rs.getBigDecimal("rin_valor_percentual"));
        }, parametros.toArray());
    }

    @Transactional(readOnly = true)
    public long contar(String municipio, String busca) {
        StringBuilder condicoes = new StringBuilder(" WHERE 1 = 1");
        List<Object> parametros = new ArrayList<>();

        if (municipio != null && !municipio.isBlank()) {
            condicoes.append(" AND m.mun_codigo_origem = ?");
            parametros.add(municipio.trim());
        }
        if (busca != null && !busca.isBlank()) {
            condicoes.append(" AND (UPPER(i.imo_codigo_car) LIKE ?"
                    + " OR UPPER(NVL(i.imo_nome, ' ')) LIKE ?)");
            String termo = "%" + busca.trim().toUpperCase() + "%";
            parametros.add(termo);
            parametros.add(termo);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM IMOVEL_RURAL i "
                        + "LEFT JOIN MUNICIPIO m ON m.mun_id = i.mun_id" + condicoes,
                Long.class, parametros.toArray());
        return total == null ? 0 : total;
    }

    // sem nome, o imovel se identifica pelo fim do CAR com o municipio
    private String rotulo(String nome, String codigoCar, String municipio) {
        if (nome != null && !nome.isBlank()) {
            return nome;
        }
        String curto = codigoCar != null && codigoCar.length() > 8
                ? codigoCar.substring(codigoCar.length() - 8) : codigoCar;
        return municipio != null ? "CAR " + curto + " — " + municipio : "CAR " + curto;
    }
}
