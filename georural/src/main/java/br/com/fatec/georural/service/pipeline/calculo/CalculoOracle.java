package br.com.fatec.georural.service.pipeline.calculo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;
import java.sql.Types;

// chama o PKG_CALCULO
@Service
@RequiredArgsConstructor
public class CalculoOracle {

    private final JdbcTemplate jdbc;

    public long calcularIrl(Long versaoId, Long execucaoId, Long regraId,
                            double minimoPct, String escopo) {
        return comUmaSaida("PKG_CALCULO.calcular_irl(?, ?, ?, ?, ?, ?)",
                versaoId, execucaoId, regraId, minimoPct, escopo);
    }

    public long calcularIae(Long versaoId, Long execucaoId, Long regraId, String escopo) {
        return comUmaSaida("PKG_CALCULO.calcular_iae(?, ?, ?, ?, ?)",
                versaoId, execucaoId, regraId, escopo);
    }

    public long calcularIcv(Long versaoId, Long execucaoId, Long regraId,
                            double minimoPct, String escopo) {
        return comUmaSaida("PKG_CALCULO.calcular_icv(?, ?, ?, ?, ?, ?)",
                versaoId, execucaoId, regraId, minimoPct, escopo);
    }

    // periodo e fator vem da regra e da competencia, resolvidos pela etapa
    public long calcularIfc(Long versaoId, Long execucaoId, Long regraId,
                            java.time.LocalDate inicio, java.time.LocalDate fim,
                            double fator, String escopo) {
        return comUmaSaida("PKG_CALCULO.calcular_ifc(?, ?, ?, ?, ?, ?, ?, ?)",
                versaoId, execucaoId, regraId,
                java.sql.Date.valueOf(inicio), java.sql.Date.valueOf(fim), fator, escopo);
    }

    public long agregarMunicipio(Long versaoId, String sigla) {
        return comUmaSaida("PKG_CALCULO.agregar_municipio(?, ?, ?)", versaoId, sigla);
    }

    public long publicar(Long versaoId) {
        return comUmaSaida("PKG_CALCULO.publicar(?, ?)", versaoId);
    }

    public long calcularIsap(Long versaoId, Long execucaoId, Long regraId,
                             String escopo, Long camadaVersaoId) {
        return comUmaSaida("PKG_CALCULO.calcular_isap(?, ?, ?, ?, ?, ?)",
                versaoId, execucaoId, regraId, escopo, camadaVersaoId);
    }

    public long calcularIsapImovel(Long imovelId, Long versaoId, Long camadaVersaoId,
                                   Long execucaoId, Long regraId) {
        return comUmaSaida("PKG_ISAP.calcular_imovel(?, ?, ?, ?, ?, ?)",
                imovelId, versaoId, camadaVersaoId, execucaoId, regraId);
    }

    public long calcularIapp(Long versaoId, Long execucaoId, Long regraId,
                             String escopo, Long appVersaoId, Long vegVersaoId) {
        return comUmaSaida("PKG_CALCULO.calcular_iapp(?, ?, ?, ?, ?, ?, ?)",
                versaoId, execucaoId, regraId, escopo, appVersaoId, vegVersaoId);
    }

    public void calcularIappImovel(Long imovelId, Long versaoId, Long appVersaoId,
                                   Long vegVersaoId, Long execucaoId, Long regraId) {
        jdbc.execute((java.sql.Connection conexao) -> {
            try (CallableStatement cs = conexao.prepareCall(
                    "BEGIN PKG_IAPP.calcular_imovel(?, ?, ?, ?, ?, ?); END;")) {
                cs.setObject(1, imovelId);
                cs.setObject(2, versaoId);
                cs.setObject(3, appVersaoId);
                cs.setObject(4, vegVersaoId);
                cs.setObject(5, execucaoId);
                cs.setObject(6, regraId);
                cs.execute();
            }
            return null;
        });
    }

    // quantos imoveis o escopo alcanca nesta versao
    public long imoveisNoEscopo(Long versaoId, String escopo) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM TABLE(PKG_CALCULO.imoveis_no_escopo(?, ?))",
                Long.class, versaoId, escopo);
        return total == null ? 0 : total;
    }

    private long comUmaSaida(String chamada, Object... entradas) {
        Long resultado = jdbc.execute((java.sql.Connection conexao) -> {
            try (CallableStatement cs = conexao.prepareCall("BEGIN " + chamada + "; END;")) {
                int posicao = 1;
                for (Object entrada : entradas) {
                    cs.setObject(posicao++, entrada);
                }
                cs.registerOutParameter(posicao, Types.NUMERIC);
                cs.execute();
                return cs.getLong(posicao);
            }
        });
        return resultado == null ? 0 : resultado;
    }
}
