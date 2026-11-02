package br.com.fatec.georural.service.pipeline.inventario;

import br.com.fatec.georural.dto.response.inventario.ImovelDoArquivoResponse;
import br.com.fatec.georural.dto.response.inventario.MunicipioDoArquivoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// o que um arquivo produziu: de quais municipios e quais imoveis
@Service
@RequiredArgsConstructor
public class ConsultaProducaoArquivoService {

    private static final int TAMANHO_MAXIMO = 500;

    private final JdbcTemplate jdbc;

    // o arquivo pode ser o pacote ou uma camada de dentro; pega os dois
    private static final String FILTRO_ARQUIVO =
            "(i.arq_bruto_id = ? OR i.arq_bruto_id IN "
                    + "(SELECT f.arq_bruto_id FROM ARQUIVO_BRUTO f WHERE f.arq_pai_id = ?))";

    @Transactional(readOnly = true)
    public List<MunicipioDoArquivoResponse> municipios(Long arquivoId) {
        return jdbc.query(
                "SELECT m.mun_id, m.mun_codigo_origem, m.mun_nome, m.mun_uf, "
                        + "COUNT(*) AS imoveis, ROUND(SUM(NVL(i.imo_area_total,0)), 4) AS area "
                        + "FROM IMOVEL_RURAL i JOIN MUNICIPIO m ON m.mun_id = i.mun_id "
                        + "WHERE " + FILTRO_ARQUIVO + " "
                        + "GROUP BY m.mun_id, m.mun_codigo_origem, m.mun_nome, m.mun_uf "
                        + "ORDER BY COUNT(*) DESC, m.mun_nome",
                (rs, linha) -> new MunicipioDoArquivoResponse(
                        rs.getLong("mun_id"),
                        rs.getString("mun_codigo_origem"),
                        rs.getString("mun_nome"),
                        rs.getString("mun_uf"),
                        rs.getLong("imoveis"),
                        rs.getBigDecimal("area")),
                arquivoId, arquivoId);
    }

    @Transactional(readOnly = true)
    public ImovelDoArquivoResponse imoveis(Long arquivoId, String municipio, String busca,
                                           int pagina, int tamanho) {
        int limite = Math.min(tamanho <= 0 ? 50 : tamanho, TAMANHO_MAXIMO);
        int atual = Math.max(pagina, 0);

        // monta a condicao em Java porque bind nulo no Oracle nao tem tipo definido
        StringBuilder condicoes = new StringBuilder("WHERE " + FILTRO_ARQUIVO);
        List<Object> parametros = new ArrayList<>();
        parametros.add(arquivoId);
        parametros.add(arquivoId);

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
                        + "LEFT JOIN MUNICIPIO m ON m.mun_id = i.mun_id " + condicoes,
                Long.class, parametros.toArray());

        List<Object> comPagina = new ArrayList<>(parametros);
        comPagina.add((long) atual * limite);
        comPagina.add(limite);

        List<ImovelDoArquivoResponse.Item> itens = jdbc.query(
                "SELECT i.imo_id, i.imo_codigo_car, i.imo_nome, i.imo_nome_origem, "
                        + "i.imo_area_total, i.ver_id, m.mun_nome, m.mun_codigo_origem "
                        + "FROM IMOVEL_RURAL i "
                        + "LEFT JOIN MUNICIPIO m ON m.mun_id = i.mun_id " + condicoes + " "
                        + "ORDER BY m.mun_nome NULLS LAST, i.imo_codigo_car "
                        + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, linha) -> {
                    String nome = rs.getString("imo_nome");
                    String cidade = rs.getString("mun_nome");
                    String car = rs.getString("imo_codigo_car");
                    Object versao = rs.getObject("ver_id");
                    return new ImovelDoArquivoResponse.Item(
                            rs.getLong("imo_id"), car, nome, rs.getString("imo_nome_origem"),
                            rotulo(nome, car, cidade), cidade,
                            rs.getString("mun_codigo_origem"),
                            rs.getBigDecimal("imo_area_total"),
                            versao == null ? null : ((Number) versao).longValue());
                },
                comPagina.toArray());

        return new ImovelDoArquivoResponse(total == null ? 0 : total, atual, limite, itens);
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

    // nome digitado pelo operador; origem MANUAL para a trilha saber
    @Transactional
    public int nomearImovel(String codigoCar, String nome) {
        String limpo = nome == null || nome.isBlank() ? null : nome.trim();

        return jdbc.update(
                "UPDATE IMOVEL_RURAL SET imo_nome = ?, imo_nome_origem = ? "
                        + "WHERE imo_codigo_car = ?",
                limpo, limpo == null ? null : "MANUAL", codigoCar);
    }
}
