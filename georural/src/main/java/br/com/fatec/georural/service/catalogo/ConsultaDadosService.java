package br.com.fatec.georural.service.catalogo;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.catalogo.CampoResponse;
import br.com.fatec.georural.dto.response.catalogo.ConjuntoIdentificacao;
import br.com.fatec.georural.dto.response.catalogo.DadosConjuntoResponse;
import br.com.fatec.georural.dto.response.catalogo.HistoricoVersoesResponse;
import br.com.fatec.georural.dto.response.catalogo.VersaoDetalheResponse;
import br.com.fatec.georural.dto.response.catalogo.VersaoResumo;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Consulta dos dados tratados e do historico de versoes de um conjunto (US#17.02).
@Service
@Transactional(readOnly = true)
public class ConsultaDadosService {

    private static final Logger log =
            LoggerFactory.getLogger(ConsultaDadosService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ConsultaDadosService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // Identificacao do conjunto mais a camada onde os dados dele moram.
    private record Alvo(
            Long conjuntoId,
            String nome,
            String tipoCamada,
            Integer srs,
            Long versaoVigenteId,
            CamadaDados camada
    ) {}

    public DadosConjuntoResponse dados(
            Long conjuntoId,
            String formatoGeometria,
            FiltroDados filtro) {
        Alvo alvo = localizar(conjuntoId);
        CamadaDados camada = alvo.camada();

        if (filtro.versaoId() != null && !camada.versionada()) {
            throw new RequisicaoInvalidaException(
                    "A camada " + alvo.tipoCamada()
                            + " nao guarda versao por registro, "
                            + "portanto nao aceita o filtro versaoId."
            );
        }

        if (filtro.imovelId() != null && !camada.filtraPorImovel()) {
            throw new RequisicaoInvalidaException(
                    "A camada " + alvo.tipoCamada()
                            + " nao se liga a um imovel, "
                            + "portanto nao aceita o filtro imovelId."
            );
        }

        VersaoResumo versao = filtro.versaoId() == null
                ? null
                : versaoDoConjunto(conjuntoId, filtro.versaoId());

        String expressaoGeometria = expressaoGeometria(camada, formatoGeometria, filtro);

        List<Object> parametros = new ArrayList<>();
        String where = montarWhere(camada, filtro, conjuntoId, parametros);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + camada.tabela() + " t" + where,
                Long.class,
                parametros.toArray()
        );

        long totalItens = total == null ? 0L : total;

        List<Map<String, Object>> registros = List.of();

        if (totalItens > 0) {
            List<Object> parametrosPagina = new ArrayList<>(parametros);

            parametrosPagina.add((long) filtro.pagina() * filtro.tamanho());
            parametrosPagina.add(filtro.tamanho());

            String sql = "SELECT t." + camada.chave()
                    + colunasSelecionadas(camada)
                    + expressaoGeometria
                    + " FROM " + camada.tabela() + " t"
                    + where
                    + " ORDER BY t." + camada.chave()
                    + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

            boolean comGeometria = !expressaoGeometria.isEmpty();

            registros = jdbc.query(
                    sql,
                    (rs, linha) -> linha(rs, camada, comGeometria),
                    parametrosPagina.toArray()
            );
        }

        return new DadosConjuntoResponse(
                identificacao(alvo),
                versao,
                camada.versionada(),
                camada.campos().stream()
                        .map(campo -> new CampoResponse(
                                campo.nome(),
                                campo.tipo().name(),
                                campo.coluna()
                        ))
                        .toList(),
                expressaoGeometria.isEmpty() ? null : "geometria",
                PaginaResponse.de(
                        registros,
                        filtro.pagina(),
                        filtro.tamanho(),
                        totalItens
                )
        );
    }

    public static final int TAMANHO_PADRAO_VERSOES = 20;
    public static final int TAMANHO_MAXIMO_VERSOES = 200;

    public HistoricoVersoesResponse versoes(
            Long conjuntoId,
            Integer paginaPedida,
            Integer tamanhoPedido) {
        int pagina = paginaPedida == null ? 0 : paginaPedida;
        int tamanho = tamanhoPedido == null
                ? TAMANHO_PADRAO_VERSOES
                : tamanhoPedido;

        if (pagina < 0) {
            throw new RequisicaoInvalidaException(
                    "A pagina nao pode ser negativa."
            );
        }

        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO_VERSOES) {
            throw new RequisicaoInvalidaException(
                    "O tamanho da pagina deve estar entre 1 e "
                            + TAMANHO_MAXIMO_VERSOES + "."
            );
        }

        Alvo alvo = localizar(conjuntoId);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM VERSAO WHERE con_id = ?",
                Long.class,
                conjuntoId
        );

        long totalItens = total == null ? 0L : total;

        List<VersaoDetalheResponse> itens = List.of();

        if (totalItens > 0) {
            Map<Long, Long> registrosPorVersao =
                    registrosPorVersao(alvo.camada(), conjuntoId);

            itens = jdbc.query("""
                    SELECT ver_id,
                           ver_numero,
                           ver_competencia,
                           ver_situacao,
                           ver_exe_id,
                           ver_justificativa,
                           ver_data_criacao,
                           ver_data_publicacao
                      FROM VERSAO
                     WHERE con_id = ?
                     ORDER BY ver_numero DESC
                     OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                    """,
                    (rs, linha) -> {
                        long versaoId = rs.getLong("ver_id");

                        return new VersaoDetalheResponse(
                                versaoId,
                                rs.getObject("ver_numero", Integer.class),
                                rs.getString("ver_competencia"),
                                rs.getString("ver_situacao"),
                                rs.getObject("ver_exe_id", Long.class),
                                rs.getString("ver_justificativa"),
                                versaoId == (alvo.versaoVigenteId() == null
                                        ? -1L
                                        : alvo.versaoVigenteId()),
                                registrosPorVersao.get(versaoId),
                                instante(rs.getTimestamp("ver_data_criacao")),
                                instante(rs.getTimestamp("ver_data_publicacao"))
                        );
                    },
                    conjuntoId,
                    (long) pagina * tamanho,
                    tamanho
            );
        }

        return new HistoricoVersoesResponse(
                identificacao(alvo),
                alvo.versaoVigenteId(),
                PaginaResponse.de(itens, pagina, tamanho, totalItens)
        );
    }

    // Conjunto, tipo da camada e a tabela de dados correspondente.
    private Alvo localizar(Long conjuntoId) {
        if (conjuntoId == null || conjuntoId <= 0) {
            throw new RecursoNaoEncontradoException(
                    "Conjunto nao encontrado: " + conjuntoId
            );
        }

        Alvo alvo = jdbc.query("""
                SELECT c.con_id,
                       c.con_nome,
                       c.con_srs,
                       c.con_ver_vigente_id,
                       t.tip_codigo
                  FROM CONJUNTO_DADOS c
                  JOIN TIPO_CONJUNTO t ON t.tip_id = c.tip_id
                 WHERE c.con_id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }

                    String tipo = rs.getString("tip_codigo");

                    return new Alvo(
                            rs.getLong("con_id"),
                            rs.getString("con_nome"),
                            tipo,
                            rs.getObject("con_srs", Integer.class),
                            rs.getObject("con_ver_vigente_id", Long.class),
                            CamadaDados.porTipoConjunto(tipo).orElse(null)
                    );
                },
                conjuntoId
        );

        if (alvo == null) {
            throw new RecursoNaoEncontradoException(
                    "Conjunto nao encontrado: " + conjuntoId
            );
        }

        if (alvo.camada() == null) {
            throw new RequisicaoInvalidaException(
                    "O tipo de camada " + alvo.tipoCamada()
                            + " nao tem tabela de dados tratados mapeada."
            );
        }

        return alvo;
    }

    private ConjuntoIdentificacao identificacao(Alvo alvo) {
        return new ConjuntoIdentificacao(
                alvo.conjuntoId(),
                alvo.nome(),
                alvo.tipoCamada(),
                alvo.camada().tabela(),
                alvo.srs()
        );
    }

    private VersaoResumo versaoDoConjunto(Long conjuntoId, Long versaoId) {
        VersaoResumo versao = jdbc.query("""
                SELECT ver_id,
                       ver_numero,
                       ver_competencia,
                       ver_situacao,
                       ver_exe_id,
                       ver_data_criacao,
                       ver_data_publicacao
                  FROM VERSAO
                 WHERE ver_id = ?
                   AND con_id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }

                    return new VersaoResumo(
                            rs.getLong("ver_id"),
                            rs.getObject("ver_numero", Integer.class),
                            rs.getString("ver_competencia"),
                            rs.getString("ver_situacao"),
                            rs.getObject("ver_exe_id", Long.class),
                            instante(rs.getTimestamp("ver_data_criacao")),
                            instante(rs.getTimestamp("ver_data_publicacao"))
                    );
                },
                versaoId,
                conjuntoId
        );

        if (versao == null) {
            throw new RecursoNaoEncontradoException(
                    "A versao " + versaoId
                            + " nao pertence ao conjunto " + conjuntoId + "."
            );
        }

        return versao;
    }

    // Quantos registros cada versao gravou na camada.
    private Map<Long, Long> registrosPorVersao(
            CamadaDados camada,
            Long conjuntoId) {
        if (!camada.versionada()) {
            return Map.of();
        }

        Map<Long, Long> contagem = new HashMap<>();

        jdbc.query("SELECT t." + camada.colunaVersao() + " AS versao, COUNT(*) AS quantos"
                        + " FROM " + camada.tabela() + " t"
                        + " WHERE t." + camada.colunaVersao() + " IS NOT NULL"
                        + "   AND EXISTS (SELECT 1 FROM VERSAO v"
                        + "                WHERE v.ver_id = t." + camada.colunaVersao()
                        + "                  AND v.con_id = ?)"
                        + " GROUP BY t." + camada.colunaVersao(),
                rs -> {
                    contagem.put(rs.getLong("versao"), rs.getLong("quantos"));
                },
                conjuntoId
        );

        return contagem;
    }

    private String colunasSelecionadas(CamadaDados camada) {
        StringBuilder colunas = new StringBuilder();

        for (CamadaDados.Campo campo : camada.campos()) {
            colunas.append(", t.").append(campo.coluna());
        }

        return colunas.toString();
    }

    // Expressao de geometria pedida, vazia quando a geometria nao foi solicitada.
    private String expressaoGeometria(
            CamadaDados camada,
            String formato,
            FiltroDados filtro) {
        String pedido = formato == null || formato.isBlank()
                ? "geojson"
                : formato.trim().toLowerCase(Locale.ROOT);

        if (!"geojson".equals(pedido) && !"wkt".equals(pedido)) {
            throw new RequisicaoInvalidaException(
                    "formatoGeometria invalido: " + formato
                            + ". Use geojson ou wkt."
            );
        }

        if (!filtro.incluirGeometria() || camada.colunaGeometria() == null) {
            return "";
        }

        return "geojson".equals(pedido)
                ? ", SDO_UTIL.TO_GEOJSON(t." + camada.colunaGeometria() + ")"
                : ", SDO_UTIL.TO_WKTGEOMETRY(t." + camada.colunaGeometria() + ")";
    }

    private String montarWhere(
            CamadaDados camada,
            FiltroDados filtro,
            Long conjuntoId,
            List<Object> parametros) {
        StringBuilder where = new StringBuilder(
                " WHERE EXISTS (SELECT 1 FROM ARQUIVO_BRUTO a"
                        + " WHERE a.arq_bruto_id = t.arq_bruto_id"
                        + " AND a.con_id = ?)"
        );

        parametros.add(conjuntoId);

        if (filtro.versaoId() != null) {
            where.append(" AND t.").append(camada.colunaVersao()).append(" = ?");
            parametros.add(filtro.versaoId());
        }

        if (filtro.imovelId() != null) {
            where.append(" AND t.").append(camada.colunaImovel()).append(" = ?");
            parametros.add(filtro.imovelId());
        }

        List<CamadaDados.Campo> textuais = camada.camposDeTexto();

        if (filtro.texto() != null && !textuais.isEmpty()) {
            where.append(" AND (");

            for (int i = 0; i < textuais.size(); i++) {
                if (i > 0) {
                    where.append(" OR ");
                }

                where.append("UPPER(NVL(t.")
                        .append(textuais.get(i).coluna())
                        .append(", ' ')) LIKE ?");

                parametros.add("%" + filtro.texto() + "%");
            }

            where.append(")");
        }

        return where.toString();
    }

    private Map<String, Object> linha(
            ResultSet rs,
            CamadaDados camada,
            boolean comGeometria) throws SQLException {
        Map<String, Object> registro = new LinkedHashMap<>();

        registro.put("id", rs.getLong(1));

        List<CamadaDados.Campo> campos = camada.campos();

        for (int i = 0; i < campos.size(); i++) {
            CamadaDados.Campo campo = campos.get(i);

            registro.put(campo.nome(), valor(rs, i + 2, campo.tipo()));
        }

        if (comGeometria) {
            registro.put(
                    "geometria",
                    geometria(rs.getString(campos.size() + 2))
            );
        }

        return registro;
    }

    private Object valor(
            ResultSet rs,
            int coluna,
            CamadaDados.Tipo tipo) throws SQLException {
        return switch (tipo) {
            case TEXTO -> rs.getString(coluna);
            case NUMERO -> rs.getBigDecimal(coluna);
            case INTEIRO -> rs.getObject(coluna, Long.class);
            case DATA -> {
                java.sql.Date data = rs.getDate(coluna);
                yield data == null ? null : data.toLocalDate();
            }
            case DATA_HORA -> instante(rs.getTimestamp(coluna));
        };
    }

    // GeoJSON entra no JSON como objeto; WKT e qualquer coisa ilegivel entram como texto.
    private Object geometria(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }

        String texto = bruto.trim();

        if (!texto.startsWith("{")) {
            return texto;
        }

        try {
            return json.readTree(texto);
        } catch (Exception e) {
            log.warn("Geometria devolvida pelo banco nao e JSON valido", e);

            return texto;
        }
    }

    private LocalDateTime instante(Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}
