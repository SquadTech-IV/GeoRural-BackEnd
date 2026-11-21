package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.dto.response.indicador.ConsolidadoMunicipiosResponse;
import br.com.fatec.georural.dto.response.indicador.ConsolidadoMunicipiosResponse.Escala;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
            String sigla,
            Long versaoId,
            boolean incluirSemDados,
            String ordenacaoPedida,
            String faixasPedidas,
            int quantidadeDeFaixas) {

        // valida os parametros antes de ir ao banco
        OrdemComparacao ordem = OrdemComparacao.de(ordenacaoPedida);
        FaixasDeComparacao.Estrategia estrategia =
                FaixasDeComparacao.Estrategia.de(faixasPedidas);

        Map<String, Object> indicador = buscarIndicador(sigla);
        Long indicadorId = numero(indicador.get("IND_ID"));
        SentidoIndicador sentido = SentidoIndicador.de(texto(indicador.get("IND_SENTIDO")));

        if (versaoId != null) {
            conferirVersao(versaoId);
        }

        List<MunicipioConsolidadoResponse> municipios =
                listar(indicadorId, versaoId, incluirSemDados);

        return montar(indicador, sentido, versaoId, municipios, ordem, estrategia,
                quantidadeDeFaixas);
    }

    private Map<String, Object> buscarIndicador(String sigla) {
        String normalizada = sigla == null ? "" : sigla.trim().toUpperCase();

        List<Map<String, Object>> achados = jdbc.queryForList(
                "SELECT ind_id, ind_sigla, ind_nome, ind_unidade, ind_sentido, "
                        + "       ind_escala_min, ind_escala_max "
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
                    dataHora(rs.getTimestamp("rmu_data_calculo")),
                    null, null, null);
        }, parametros);
    }

    private ConsolidadoMunicipiosResponse montar(
            Map<String, Object> indicador,
            SentidoIndicador sentido,
            Long versaoId,
            List<MunicipioConsolidadoResponse> municipios,
            OrdemComparacao ordem,
            FaixasDeComparacao.Estrategia estrategia,
            int quantidadeDeFaixas) {

        String sigla = String.valueOf(indicador.get("IND_SIGLA"));

        List<BigDecimal> valores = municipios.stream()
                .filter(MunicipioConsolidadoResponse::temDados)
                .map(MunicipioConsolidadoResponse::valorAgregado)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        FaixasDeComparacao.Resultado faixas = FaixasDeComparacao.calcular(
                estrategia, quantidadeDeFaixas, valores,
                decimal(indicador.get("IND_ESCALA_MIN")),
                decimal(indicador.get("IND_ESCALA_MAX")),
                sentido);

        // ordena e so depois numera a posicao, para a posicao ser a da lista
        List<MunicipioConsolidadoResponse> ordenados = new ArrayList<>(municipios);
        ordenados.sort(ordem.comparador(sentido));

        List<MunicipioConsolidadoResponse> classificados = new ArrayList<>();
        int posicao = 0;
        for (MunicipioConsolidadoResponse m : ordenados) {
            Integer lugar = m.temDados() ? ++posicao : null;
            classificados.add(m.comClassificacao(
                    faixas.faixaDe(m.valorAgregado()),
                    faixas.rotuloDe(m.valorAgregado()),
                    lugar));
        }

        List<MunicipioConsolidadoResponse> comDados = classificados.stream()
                .filter(MunicipioConsolidadoResponse::temDados)
                .toList();

        long imoveis = comDados.stream()
                .map(MunicipioConsolidadoResponse::imoveis)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        List<ConsolidadoMunicipiosResponse.Versao> versoes = versoesEnvolvidas(comDados);

        return new ConsolidadoMunicipiosResponse(
                new ConsolidadoMunicipiosResponse.Indicador(
                        numero(indicador.get("IND_ID")),
                        sigla,
                        String.valueOf(indicador.get("IND_NOME")),
                        texto(indicador.get("IND_UNIDADE")),
                        sentido.name()),
                versoes,
                versaoId == null,
                situacaoBoa(sigla),
                municipios.size(),
                comDados.size(),
                municipios.size() - comDados.size(),
                imoveis,
                resumo(valores),
                ordem.name(),
                new Escala(faixas.escalaMin(), faixas.escalaMax(),
                        faixas.origemDaEscala(), estrategia.name()),
                faixas.faixas(),
                avisos(sigla, municipios, comDados, versoes, faixas),
                classificados);
    }

    // o que a tela e a equipe precisam saber para nao interpretar errado
    private List<String> avisos(
            String sigla,
            List<MunicipioConsolidadoResponse> municipios,
            List<MunicipioConsolidadoResponse> comDados,
            List<ConsolidadoMunicipiosResponse.Versao> versoes,
            FaixasDeComparacao.Resultado faixas) {

        List<String> avisos = new ArrayList<>(faixas.avisos());

        if (municipios.isEmpty()) {
            avisos.add("A tabela MUNICIPIO esta vazia: a malha municipal ainda nao "
                    + "foi carregada, entao nao ha o que comparar.");
        } else if (comDados.isEmpty()) {
            avisos.add("Nenhum municipio tem resultado agregado para " + sigla
                    + " nesta versao. Quem preenche RESULTADO_MUNICIPIO e a etapa "
                    + "QUALIDADE do pipeline.");
        }

        if (versoes.size() > 1) {
            avisos.add("Os resultados vigentes vieram de " + versoes.size()
                    + " versoes diferentes, porque o vigente e por par "
                    + "(municipio, indicador). Informe versaoId para olhar uma so.");
        }

        return avisos;
    }


    private List<ConsolidadoMunicipiosResponse.Versao> versoesEnvolvidas(
            List<MunicipioConsolidadoResponse> comDados) {

        List<Long> ids = comDados.stream()
                .map(MunicipioConsolidadoResponse::versaoId)
                .filter(Objects::nonNull)
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
    private Resumo resumo(List<BigDecimal> valoresOrdenados) {
        if (valoresOrdenados.isEmpty()) {
            return new Resumo(null, null, null, null);
        }

        BigDecimal soma = valoresOrdenados.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal media = soma.divide(
                BigDecimal.valueOf(valoresOrdenados.size()), 4, RoundingMode.HALF_UP);

        return new Resumo(
                valoresOrdenados.get(0),
                valoresOrdenados.get(valoresOrdenados.size() - 1),
                media,
                mediana(valoresOrdenados));
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

    private static BigDecimal decimal(Object valor) {
        if (valor == null) {
            return null;
        }

        if (valor instanceof BigDecimal bd) {
            return bd;
        }

        return new BigDecimal(String.valueOf(valor));
    }

    private static String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private static LocalDateTime dataHora(Timestamp valor) {
        return valor == null ? null : valor.toLocalDateTime();
    }
}
