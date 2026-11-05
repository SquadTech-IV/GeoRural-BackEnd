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

    public long calcularIcv(Long versaoId, Long execucaoId, Long regraCalculoId, Double minimoPct, String escopo) {
        return comUmaSaida("PKG_CALCULO_ICV.calcular_icv(?, ?, ?, ?, ?, ?)",
                versaoId, execucaoId, regraCalculoId, minimoPct, escopo);
    }

    public long agregarMunicipio(Long versaoId, String sigla) {
        return comUmaSaida("PKG_CALCULO.agregar_municipio(?, ?, ?)", versaoId, sigla);
    }

    public long publicar(Long versaoId) {
        return comUmaSaida("PKG_CALCULO.publicar(?, ?)", versaoId);
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
