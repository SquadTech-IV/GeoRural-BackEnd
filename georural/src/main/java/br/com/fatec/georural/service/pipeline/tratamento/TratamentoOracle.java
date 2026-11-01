package br.com.fatec.georural.service.pipeline.tratamento;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;
import java.sql.Types;

// chama o PKG_TRATAMENTO; todo o trabalho geoespacial acontece dentro do Oracle
@Slf4j
@Service
@RequiredArgsConstructor
public class TratamentoOracle {

    private final JdbcTemplate jdbc;

    // precisa ser a primeira chamada da transacao (ORA-12841)
    public void prepararSessao() {
        jdbc.update("BEGIN PKG_TRATAMENTO.preparar_sessao(); END;");
    }

    public long sanear(Long execucaoId) {
        return comUmaSaida("PKG_TRATAMENTO.sanear(?, ?)", execucaoId);
    }

    public long rejeitarInvalidas(Long execucaoId, Long validacaoId) {
        return comUmaSaida("PKG_TRATAMENTO.rejeitar_invalidas(?, ?, ?)", execucaoId, validacaoId);
    }

    public long recortarUf(Long execucaoId, String uf) {
        return comUmaSaida("PKG_TRATAMENTO.recortar_uf(?, ?, ?)", execucaoId, uf);
    }

    public long rejeitarForaDaUf(Long execucaoId, Long validacaoId, String uf) {
        return comUmaSaida("PKG_TRATAMENTO.rejeitar_fora_da_uf(?, ?, ?, ?)", execucaoId, validacaoId, uf);
    }

    public long calcularAreas(Long execucaoId) {
        return comUmaSaida("PKG_TRATAMENTO.calcular_areas(?, ?)", execucaoId);
    }

    public long rejeitarAreaNula(Long execucaoId, Long validacaoId) {
        return comUmaSaida("PKG_TRATAMENTO.rejeitar_area_nula(?, ?, ?)", execucaoId, validacaoId);
    }

    public long rejeitarRlSemImovel(Long execucaoId, Long validacaoId) {
        return comUmaSaida("PKG_TRATAMENTO.rejeitar_rl_sem_imovel(?, ?, ?)", execucaoId, validacaoId);
    }

    public long avisarAreaIncompativel(Long execucaoId, Long validacaoId, double toleranciaPct) {
        return comUmaSaida("PKG_TRATAMENTO.avisar_area_incompativel(?, ?, ?, ?)",
                execucaoId, validacaoId, toleranciaPct);
    }

    public long carregarImovel(Long execucaoId, Long versaoId) {
        return comUmaSaida("PKG_TRATAMENTO.carregar_imovel(?, ?, ?)", execucaoId, versaoId);
    }

    public long carregarReservaLegal(Long execucaoId, Long versaoId) {
        return comUmaSaida("PKG_TRATAMENTO.carregar_reserva_legal(?, ?, ?)", execucaoId, versaoId);
    }

    public long carregarEmbargo(Long execucaoId, Long versaoId) {
        return comUmaSaida("PKG_TRATAMENTO.carregar_embargo(?, ?, ?)", execucaoId, versaoId);
    }

    public long carregarMunicipio(Long execucaoId) {
        return comUmaSaida("PKG_TRATAMENTO.carregar_municipio(?, ?)", execucaoId);
    }

    public Vinculo vincularMunicipio(Long execucaoId, Long versaoId) {
        return jdbc.execute((java.sql.Connection conexao) -> {
            try (CallableStatement cs = conexao.prepareCall(
                    "BEGIN PKG_TRATAMENTO.vincular_municipio(?, ?, ?, ?); END;")) {
                cs.setLong(1, execucaoId);
                cs.setLong(2, versaoId);
                cs.registerOutParameter(3, Types.NUMERIC);
                cs.registerOutParameter(4, Types.NUMERIC);
                cs.execute();
                return new Vinculo(cs.getLong(3), cs.getLong(4));
            }
        });
    }

    public String impressao(Long execucaoId) {
        return jdbc.queryForObject("SELECT PKG_TRATAMENTO.impressao(?) FROM DUAL",
                String.class, execucaoId);
    }

    public long restantes(Long execucaoId) {
        Long total = jdbc.queryForObject("SELECT PKG_TRATAMENTO.restantes(?) FROM DUAL",
                Long.class, execucaoId);
        return total == null ? 0 : total;
    }

    // chama uma procedure cujo ultimo parametro e a contagem de saida
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

    public record Vinculo(long porCodigo, long porGeometria) {

        public long total() {
            return porCodigo + porGeometria;
        }
    }
}
