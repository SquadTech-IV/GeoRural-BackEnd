package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.dto.response.indicador.ConsolidadoMunicipiosResponse;
import br.com.fatec.georural.dto.response.indicador.ConsolidadoMunicipiosResponse.Resumo;
import br.com.fatec.georural.dto.response.indicador.MunicipioConsolidadoResponse;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConsolidadoMunicipioService {

    private final JdbcTemplate jdbc;


    private static final Map<String, String> SITUACAO_BOA = Map.of(
            "IAE", "SEM_EMBARGO",
            "ICV", "CONSERVADO");

    private static final String SITUACAO_BOA_PADRAO = "CONFORME";

    @Transactional(readOnly = true)
    public ConsolidadoMunicipiosResponse porMunicipio(
            String sigla, Long versaoId, boolean incluirSemDados) {

        Map<String, Object> indicador = buscarIndicador(sigla);
        Long indicadorId = numero(indicador.get("IND_ID"));

        if (versaoId != null) {
            conferirVersao(versaoId);
        }

        List<MunicipioConsolidadoResponse> municipios =
                listar(indicadorId, versaoId, incluirSemDados);

        return montar(indicador, versaoId, municipios);
    }

    private Map<String, Object> buscarIndicador(String sigla) {
        String normalizada = sigla == null ? "" : sigla.trim().toUpperCase();

        List<Map<String, Object>> achados = jdbc.queryForList(
                "SELECT ind_id, ind_sigla, ind_nome, ind_unidade "
                        + "  FROM INDICADOR WHERE UPPER(ind_sigla) = ?",
                normalizada);

        if (achados.isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Indicador " + sigla + " nao cadastrado.");
        }

        return achados.get(0);
    }

    private void conferirVersao(Long versaoId) {
        Integer existe = jdbc.queryForObject(
                "SELECT COUNT(*) FROM VERSAO WHERE ver_id = ?", Integer.class, versaoId);

        if (existe == null || existe == 0) {
            throw new RecursoNaoEncontradoException(
                    "Versao " + versaoId + " nao encontrada.");
        }
    }

    private List<MunicipioConsolidadoResponse> listar(
            Long indicadorId, Long versaoId, boolean incluirSemDados) {


        String recorteDaVersao = versaoId == null
                ? " AND r.rmu_vigente = 'S' "
                : " AND r.ver_id = ? ";

        String juncao = incluirSemDados ? " LEFT JOIN " : " JOIN ";

        String sql = "SELECT m.mun_id, m.mun_codigo_origem, m.mun_nome, m.mun_uf, "
                + "       m.mun_area_ha, "
                + "       r.rmu_id, r.rmu_valor_agregado, r.rmu_situacao, r.ver_id, "
                + "       r.rmu_data_calculo, "
                + "       JSON_VALUE(r.rmu_detalhes, '$.imoveis' RETURNING NUMBER) AS imoveis, "
                + "       JSON_VALUE(r.rmu_detalhes, '$.conformes' RETURNING NUMBER) AS conformes, "
                + "       JSON_VALUE(r.rmu_detalhes, '$.soma_hectares' RETURNING NUMBER) AS soma_ha "
                + "  FROM MUNICIPIO m "
                + juncao + " RESULTADO_MUNICIPIO r "
                + "    ON r.mun_id = m.mun_id "
                + "   AND r.ind_id = ? "
                + recorteDaVersao
                + " ORDER BY m.mun_nome, m.mun_codigo_origem";

        Object[] parametros = versaoId == null
                ? new Object[]{indicadorId}
                : new Object[]{indicadorId, versaoId};

        return jdbc.query(sql, (rs, linha) -> {
            boolean temDados = rs.getObject("rmu_id") != null;
            BigDecimal imoveis = rs.getBigDecimal("imoveis");
            BigDecimal conformes = rs.getBigDecimal("conformes");

            return new MunicipioConsolidadoResponse(
                    numero(rs.getObject("mun_id")),
                    rs.getString("mun_codigo_origem"),
                    rs.getString("mun_nome"),
                    rs.getString("mun_uf"),
                    rs.getBigDecimal("mun_area_ha"),
                    temDados,
                    rs.getBigDecimal("rmu_valor_agregado"),
                    inteiro(imoveis),
                    inteiro(conformes),
                    percentual(conformes, imoveis),
                    rs.getBigDecimal("soma_ha"),
                    rs.getString("rmu_situacao"),
                    numero(rs.getObject("ver_id")),
                    dataHora(rs.getTimestamp("rmu_data_calculo")));
        }, parametros);
    }

    private ConsolidadoMunicipiosResponse montar(
            Map<String, Object> indicador,
            Long versaoId,
            List<MunicipioConsolidadoResponse> municipios) {

        List<MunicipioConsolidadoResponse> comDados = municipios.stream()
                .filter(MunicipioConsolidadoResponse::temDados)
                .toList();

        long imoveis = comDados.stream()
                .map(MunicipioConsolidadoResponse::imoveis)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        return new ConsolidadoMunicipiosResponse(
                new ConsolidadoMunicipiosResponse.Indicador(
                        numero(indicador.get("IND_ID")),
                        String.valueOf(indicador.get("IND_SIGLA")),
                        String.valueOf(indicador.get("IND_NOME")),
                        texto(indicador.get("IND_UNIDADE"))),
                versoesEnvolvidas(comDados),
                versaoId == null,
                situacaoBoa(String.valueOf(indicador.get("IND_SIGLA"))),
                municipios.size(),
                comDados.size(),
                municipios.size() - comDados.size(),
                imoveis,
                resumo(comDados),
                municipios);
    }


    private List<ConsolidadoMunicipiosResponse.Versao> versoesEnvolvidas(
            List<MunicipioConsolidadoResponse> comDados) {

        List<Long> ids = comDados.stream()
                .map(MunicipioConsolidadoResponse::versaoId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        if (ids.isEmpty()) {
            return List.of();
        }

        String marcadores = String.join(",", ids.stream().map(i -> "?").toList());

        return jdbc.query("SELECT v.ver_id, v.ver_numero, v.ver_competencia, "
                        + "       v.ver_situacao, v.con_id, c.con_nome "
                        + "  FROM VERSAO v "
                        + "  JOIN CONJUNTO_DADOS c ON c.con_id = v.con_id "
                        + " WHERE v.ver_id IN (" + marcadores + ") "
                        + " ORDER BY v.ver_id",
                (rs, linha) -> new ConsolidadoMunicipiosResponse.Versao(
                        numero(rs.getObject("ver_id")),
                        numero(rs.getObject("ver_numero")),
                        rs.getString("ver_competencia"),
                        rs.getString("ver_situacao"),
                        numero(rs.getObject("con_id")),
                        rs.getString("con_nome")),
                ids.toArray());
    }

    // minimo, maximo, media e mediana sobre os municipios com dado, calculados
    private Resumo resumo(List<MunicipioConsolidadoResponse> comDados) {
        List<BigDecimal> valores = comDados.stream()
                .map(MunicipioConsolidadoResponse::valorAgregado)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();

        if (valores.isEmpty()) {
            return new Resumo(null, null, null, null);
        }

        BigDecimal soma = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal media = soma.divide(
                BigDecimal.valueOf(valores.size()), 4, RoundingMode.HALF_UP);

        return new Resumo(
                valores.get(0),
                valores.get(valores.size() - 1),
                media,
                mediana(valores));
    }

    private static BigDecimal mediana(List<BigDecimal> ordenados) {
        int meio = ordenados.size() / 2;

        if (ordenados.size() % 2 == 1) {
            return ordenados.get(meio);
        }

        return ordenados.get(meio - 1)
                .add(ordenados.get(meio))
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private static String situacaoBoa(String sigla) {
        return SITUACAO_BOA.getOrDefault(
                sigla == null ? "" : sigla.toUpperCase(), SITUACAO_BOA_PADRAO);
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal total) {
        if (parte == null || total == null || total.signum() == 0) {
            return null;
        }

        return parte.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP);
    }

    private static Long inteiro(BigDecimal valor) {
        return valor == null ? null : valor.longValue();
    }

    private static Long numero(Object valor) {
        return valor == null ? null : ((Number) valor).longValue();
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private static LocalDateTime dataHora(Timestamp valor) {
        return valor == null ? null : valor.toLocalDateTime();
    }
}
