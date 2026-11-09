package br.com.fatec.georural.service.catalogo;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.catalogo.AcervoResumo;
import br.com.fatec.georural.dto.response.catalogo.CatalogoDetalheResponse;
import br.com.fatec.georural.dto.response.catalogo.CatalogoItemResponse;
import br.com.fatec.georural.dto.response.catalogo.EntradaApiResumo;
import br.com.fatec.georural.dto.response.catalogo.FiltrosCatalogoResponse;
import br.com.fatec.georural.dto.response.catalogo.FonteResumo;
import br.com.fatec.georural.dto.response.catalogo.IndicadorResumo;
import br.com.fatec.georural.dto.response.catalogo.TipoResumo;
import br.com.fatec.georural.dto.response.catalogo.VersaoResumo;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Consulta somente leitura do catalogo de dados da plataforma (US#17.01).
@Service
@Transactional(readOnly = true)
public class ConsultaCatalogoService {

    private static final String COLUNAS = """
            SELECT c.con_id,
                   c.con_nome,
                   c.con_descricao,
                   c.con_srs,
                   c.con_cobertura,
                   c.con_periodicidade,
                   c.con_situacao,
                   c.con_forma_entrada,
                   c.con_data_cadastro,
                   f.fon_id,
                   f.fon_nome,
                   f.fon_sigla,
                   f.fon_esfera,
                   f.fon_status,
                   t.tip_codigo,
                   t.tip_nome,
                   t.tip_formatos,
                   t.tip_indicadores,
                   v.ver_id,
                   v.ver_numero,
                   v.ver_competencia,
                   v.ver_situacao,
                   v.ver_exe_id,
                   v.ver_data_criacao,
                   v.ver_data_publicacao,
                   (SELECT COUNT(*)
                      FROM VERSAO vv
                     WHERE vv.con_id = c.con_id) AS total_versoes,
                   (SELECT COUNT(*)
                      FROM ARQUIVO_BRUTO a
                     WHERE a.con_id = c.con_id
                       AND a.arq_pai_id IS NULL) AS total_arquivos,
                   (SELECT NVL(SUM(a.arq_tamanho_bytes), 0)
                      FROM ARQUIVO_BRUTO a
                     WHERE a.con_id = c.con_id
                       AND a.arq_pai_id IS NULL) AS bytes_armazenados,
                   (SELECT MAX(a.arq_data_upload)
                      FROM ARQUIVO_BRUTO a
                     WHERE a.con_id = c.con_id) AS ultimo_envio
            """;

    private static final String ORIGEM = """
              FROM CONJUNTO_DADOS c
              JOIN FONTE_DO_DADO f ON f.fon_id = c.fon_id
              JOIN TIPO_CONJUNTO t ON t.tip_id = c.tip_id
              LEFT JOIN VERSAO v   ON v.ver_id = c.con_ver_vigente_id
            """;

    // Expressao de ORDER BY aceita em cada opcao de ordenacao.
    private static final Map<String, String> ORDENS = Map.of(
            "nome", "f.fon_nome, c.con_nome",
            "atualizacao", "ultimo_envio DESC NULLS LAST, c.con_nome",
            "acervo", "total_arquivos DESC, c.con_nome",
            "situacao", "c.con_situacao, f.fon_nome, c.con_nome",
            "cadastro", "c.con_data_cadastro DESC, c.con_nome"
    );

    private final JdbcTemplate jdbc;

    public ConsultaCatalogoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Pagina do catalogo com os filtros aplicados.
    public PaginaResponse<CatalogoItemResponse> listar(
            FiltroCatalogo filtro) {
        List<Object> parametros = new ArrayList<>();
        String where = montarWhere(filtro, parametros);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + ORIGEM + where,
                Long.class,
                parametros.toArray()
        );

        long totalItens = total == null ? 0L : total;

        if (totalItens == 0) {
            return PaginaResponse.de(
                    List.of(),
                    filtro.pagina(),
                    filtro.tamanho(),
                    0L
            );
        }

        List<Object> parametrosPagina = new ArrayList<>(parametros);

        parametrosPagina.add((long) filtro.pagina() * filtro.tamanho());
        parametrosPagina.add(filtro.tamanho());

        String sql = COLUNAS
                + ORIGEM
                + where
                + " ORDER BY " + ORDENS.get(filtro.ordenar())
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        Map<String, IndicadorResumo> indicadores = indicadoresPorSigla();

        List<CatalogoItemResponse> itens = jdbc.query(
                sql,
                mapeador(indicadores),
                parametrosPagina.toArray()
        );

        return PaginaResponse.de(
                itens,
                filtro.pagina(),
                filtro.tamanho(),
                totalItens
        );
    }

    // Detalhe de um conjunto, com esquema tecnico, versoes e competencias.
    public CatalogoDetalheResponse detalhar(Long conjuntoId) {
        if (conjuntoId == null || conjuntoId <= 0) {
            throw new RecursoNaoEncontradoException(
                    "Conjunto nao encontrado: " + conjuntoId
            );
        }

        List<CatalogoItemResponse> achados = jdbc.query(
                COLUNAS + ORIGEM + " WHERE c.con_id = ?",
                mapeador(indicadoresPorSigla()),
                conjuntoId
        );

        if (achados.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Conjunto nao encontrado: " + conjuntoId
            );
        }

        CatalogoItemResponse conjunto = achados.get(0);

        return new CatalogoDetalheResponse(
                conjunto,
                esquemaTecnico(conjuntoId),
                entradaApi(conjuntoId),
                competencias(conjuntoId),
                versoes(conjuntoId),
                links(conjuntoId)
        );
    }

    // Valores que a listagem aceita em cada filtro.
    public FiltrosCatalogoResponse filtros() {
        List<FonteResumo> fontes = jdbc.query("""
                SELECT fon_id, fon_nome, fon_sigla, fon_esfera, fon_status
                  FROM FONTE_DO_DADO
                 ORDER BY fon_nome
                """, (rs, linha) -> new FonteResumo(
                rs.getLong("fon_id"),
                rs.getString("fon_nome"),
                rs.getString("fon_sigla"),
                rs.getString("fon_esfera"),
                "S".equals(rs.getString("fon_status"))
        ));

        List<TipoResumo> tipos = jdbc.query("""
                SELECT tip_codigo, tip_nome, tip_formatos
                  FROM TIPO_CONJUNTO
                 ORDER BY tip_nome
                """, (rs, linha) -> new TipoResumo(
                rs.getString("tip_codigo"),
                rs.getString("tip_nome"),
                lista(rs.getString("tip_formatos"))
        ));

        List<String> coberturas = jdbc.queryForList("""
                SELECT DISTINCT con_cobertura
                  FROM CONJUNTO_DADOS
                 WHERE con_cobertura IS NOT NULL
                 ORDER BY 1
                """, String.class);

        return new FiltrosCatalogoResponse(
                fontes,
                tipos,
                List.of("RASCUNHO", "EM_VALIDACAO", "PUBLICADO", "ARQUIVADO"),
                List.of("UPLOAD", "API", "AMBOS"),
                coberturas,
                FiltroCatalogo.ORDENACOES
        );
    }

    // Monta o WHERE e acumula os parametros na mesma ordem.
    private String montarWhere(
            FiltroCatalogo filtro,
            List<Object> parametros) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        if (!filtro.incluirInativos()) {
            where.append("""
                       AND f.fon_status = 'S'
                       AND c.con_situacao <> 'ARQUIVADO'
                    """);
        }

        if (filtro.fonteId() != null) {
            where.append(" AND c.fon_id = ?");
            parametros.add(filtro.fonteId());
        }

        if (filtro.fonteSigla() != null) {
            where.append(" AND UPPER(f.fon_sigla) = ?");
            parametros.add(filtro.fonteSigla());
        }

        if (filtro.tipo() != null) {
            where.append(" AND UPPER(t.tip_codigo) = ?");
            parametros.add(filtro.tipo());
        }

        if (filtro.situacao() != null) {
            where.append(" AND c.con_situacao = ?");
            parametros.add(filtro.situacao());
        }

        if (filtro.formaEntrada() != null) {
            where.append(" AND c.con_forma_entrada = ?");
            parametros.add(filtro.formaEntrada());
        }

        if (filtro.cobertura() != null) {
            where.append(" AND UPPER(c.con_cobertura) = ?");
            parametros.add(filtro.cobertura());
        }

        if (filtro.comVersaoVigente()) {
            where.append(" AND c.con_ver_vigente_id IS NOT NULL");
        }

        if (filtro.competencia() != null) {
            where.append("""
                       AND EXISTS (SELECT 1
                                     FROM ARQUIVO_BRUTO a
                                    WHERE a.con_id = c.con_id
                                      AND a.arq_competencia = ?)
                    """);
            parametros.add(filtro.competencia());
        }

        // Busca textual no campo tip_indicadores, que e texto livre de cadastro.
        if (filtro.indicador() != null) {
            where.append(" AND UPPER(NVL(t.tip_indicadores, ' ')) LIKE ?");
            parametros.add("%" + filtro.indicador() + "%");
        }

        if (filtro.texto() != null) {
            where.append("""
                       AND (UPPER(c.con_nome) LIKE ?
                         OR UPPER(NVL(c.con_descricao, ' ')) LIKE ?
                         OR UPPER(f.fon_nome) LIKE ?
                         OR UPPER(NVL(f.fon_sigla, ' ')) LIKE ?
                         OR UPPER(t.tip_nome) LIKE ?)
                    """);

            String curinga = "%" + filtro.texto() + "%";

            for (int i = 0; i < 5; i++) {
                parametros.add(curinga);
            }
        }

        return where.toString();
    }

    private RowMapper<CatalogoItemResponse> mapeador(
            Map<String, IndicadorResumo> indicadores) {
        return (rs, linha) -> new CatalogoItemResponse(
                rs.getLong("con_id"),
                rs.getString("con_nome"),
                rs.getString("con_descricao"),
                new FonteResumo(
                        rs.getLong("fon_id"),
                        rs.getString("fon_nome"),
                        rs.getString("fon_sigla"),
                        rs.getString("fon_esfera"),
                        "S".equals(rs.getString("fon_status"))
                ),
                new TipoResumo(
                        rs.getString("tip_codigo"),
                        rs.getString("tip_nome"),
                        lista(rs.getString("tip_formatos"))
                ),
                rs.getObject("con_srs", Integer.class),
                rs.getString("con_cobertura"),
                rs.getString("con_periodicidade"),
                rs.getString("con_situacao"),
                rs.getString("con_forma_entrada"),
                !"ARQUIVADO".equals(rs.getString("con_situacao"))
                        && "S".equals(rs.getString("fon_status")),
                versaoVigente(rs),
                new AcervoResumo(
                        rs.getLong("total_versoes"),
                        rs.getLong("total_arquivos"),
                        rs.getLong("bytes_armazenados"),
                        instante(rs.getTimestamp("ultimo_envio"))
                ),
                resolver(rs.getString("tip_indicadores"), indicadores),
                rs.getString("tip_indicadores"),
                instante(rs.getTimestamp("con_data_cadastro"))
        );
    }

    private VersaoResumo versaoVigente(ResultSet rs)
            throws SQLException {
        Long versaoId = rs.getObject("ver_id", Long.class);

        if (versaoId == null) {
            return null;
        }

        return new VersaoResumo(
                versaoId,
                rs.getObject("ver_numero", Integer.class),
                rs.getString("ver_competencia"),
                rs.getString("ver_situacao"),
                rs.getObject("ver_exe_id", Long.class),
                instante(rs.getTimestamp("ver_data_criacao")),
                instante(rs.getTimestamp("ver_data_publicacao"))
        );
    }

    private String esquemaTecnico(Long conjuntoId) {
        return jdbc.query("""
                SELECT t.tip_esquema
                  FROM CONJUNTO_DADOS c
                  JOIN TIPO_CONJUNTO t ON t.tip_id = c.tip_id
                 WHERE c.con_id = ?
                """,
                rs -> rs.next() ? rs.getString(1) : null,
                conjuntoId
        );
    }

    private EntradaApiResumo entradaApi(Long conjuntoId) {
        return jdbc.query("""
                SELECT con_api_endpoint,
                       con_api_formato,
                       con_api_auth
                  FROM CONJUNTO_DADOS
                 WHERE con_id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }

                    return new EntradaApiResumo(
                            rs.getString("con_api_endpoint"),
                            rs.getString("con_api_formato"),
                            rs.getString("con_api_auth") != null
                    );
                },
                conjuntoId
        );
    }

    private List<String> competencias(Long conjuntoId) {
        return jdbc.queryForList("""
                SELECT DISTINCT arq_competencia
                  FROM ARQUIVO_BRUTO
                 WHERE con_id = ?
                 ORDER BY 1 DESC
                """, String.class, conjuntoId);
    }

    private List<VersaoResumo> versoes(Long conjuntoId) {
        return jdbc.query("""
                SELECT ver_id,
                       ver_numero,
                       ver_competencia,
                       ver_situacao,
                       ver_exe_id,
                       ver_data_criacao,
                       ver_data_publicacao
                  FROM VERSAO
                 WHERE con_id = ?
                 ORDER BY ver_numero DESC
                """,
                (rs, linha) -> new VersaoResumo(
                        rs.getLong("ver_id"),
                        rs.getObject("ver_numero", Integer.class),
                        rs.getString("ver_competencia"),
                        rs.getString("ver_situacao"),
                        rs.getObject("ver_exe_id", Long.class),
                        instante(rs.getTimestamp("ver_data_criacao")),
                        instante(rs.getTimestamp("ver_data_publicacao"))
                ),
                conjuntoId
        );
    }

    // Rotas relacionadas ao conjunto, para quem integra navegar sem adivinhar.
    private Map<String, String> links(Long conjuntoId) {
        Map<String, String> links = new LinkedHashMap<>();

        links.put("self", "/api/v1/catalogo/" + conjuntoId);
        links.put("versoes", "/api/versoes?conjuntoId=" + conjuntoId);
        links.put("arquivos", "/api/arquivos/brutos?conjuntoId=" + conjuntoId);
        links.put("execucoes", "/api/v1/qualidade/execucoes?conjuntoId=" + conjuntoId);
        links.put("quarentena", "/api/v1/qualidade/quarentena?conjuntoId=" + conjuntoId);

        return links;
    }

    private Map<String, IndicadorResumo> indicadoresPorSigla() {
        Map<String, IndicadorResumo> mapa = new LinkedHashMap<>();

        jdbc.query("""
                SELECT ind_sigla, ind_nome, ind_unidade
                  FROM INDICADOR
                 ORDER BY ind_sigla
                """, rs -> {
            String sigla = rs.getString("ind_sigla");

            mapa.put(
                    sigla.toUpperCase(Locale.ROOT),
                    new IndicadorResumo(
                            sigla,
                            rs.getString("ind_nome"),
                            rs.getString("ind_unidade")
                    )
            );
        });

        return mapa;
    }

    // Converte o texto livre de tip_indicadores nos indicadores cadastrados.
    private List<IndicadorResumo> resolver(
            String texto,
            Map<String, IndicadorResumo> indicadores) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }

        List<IndicadorResumo> achados = new ArrayList<>();

        for (String parte : texto.split("[,;/]")) {
            IndicadorResumo indicador = indicadores.get(
                    parte.trim().toUpperCase(Locale.ROOT)
            );

            if (indicador != null && !achados.contains(indicador)) {
                achados.add(indicador);
            }
        }

        return achados;
    }

    private List<String> lista(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }

        return Arrays.stream(texto.split(","))
                .map(String::trim)
                .filter(parte -> !parte.isEmpty())
                .toList();
    }

    private LocalDateTime instante(Timestamp timestamp) {
        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }
}
