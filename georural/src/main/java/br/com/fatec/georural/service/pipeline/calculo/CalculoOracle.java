package br.com.fatec.georural.service.pipeline.calculo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.List;

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

    public long calcularIapp(Long versaoId, Long execucaoId, Long regraId,
                             String escopo, Long appVersaoId, Long vegVersaoId) {
        return comUmaSaida("PKG_CALCULO.calcular_iapp(?, ?, ?, ?, ?, ?, ?)",
                versaoId, execucaoId, regraId, escopo, appVersaoId, vegVersaoId);
    }

    public long calcularIdesmat(Long versaoId, Long execucaoId, Long regraId, String escopo) {
        return comUmaSaida("PKG_CALCULO.calcular_idesmat(?, ?, ?, ?, ?)",
                versaoId, execucaoId, regraId, escopo);
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

    // copia o resultado do IAPP e do ISAP para a RESULTADO_INDICADOR
    public long projetar(Long versaoId, Long execucaoId, String sigla) {
        return comUmaSaida("PKG_PROJECAO.projetar(?, ?, ?, ?)", versaoId, execucaoId, sigla);
    }

    // quantos imoveis o escopo alcanca nesta versao
    public long imoveisNoEscopo(Long versaoId, String escopo) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM TABLE(PKG_CALCULO.imoveis_no_escopo(?, ?))",
                Long.class, versaoId, escopo);
        return total == null ? 0 : total;
    }

    // Quais camadas este arquivo gravou no banco. Sai das tabelas de destino, e nao
    // do tipo do conjunto nem do contexto, por dois motivos: um zip do CAR traz varias
    // camadas num conjunto tipado IMOVEL, e o Airflow roda uma etapa por chamada de
    // API, cada uma com contexto novo -- o que o TRATAMENTO anotou no contexto nao
    // chega no CALCULO nesse caminho.
    //
    // O CONNECT BY existe porque cada camada de dentro do zip e um ARQUIVO_BRUTO
    // proprio, filho do zip pelo arq_pai_id, e o TratamentoEtapa grava nas tabelas o
    // id DA CAMADA (camada.arquivoId()), nao o do zip. Procurar so pelo id do zip
    // achava apenas o imovel. A hierarquia cobre zip dentro de zip em qualquer nivel.
    public List<String> camadasDoArquivo(Long arquivoId) {
        return jdbc.queryForList("""
                WITH alvo AS (
                  SELECT arq_bruto_id AS id
                    FROM ARQUIVO_BRUTO
                   START WITH arq_bruto_id = ?
                   CONNECT BY PRIOR arq_bruto_id = arq_pai_id
                )
                SELECT 'IMOVEL' AS camada FROM DUAL WHERE EXISTS
                      (SELECT 1 FROM IMOVEL_RURAL     x JOIN alvo a ON a.id = x.arq_bruto_id)
                UNION ALL SELECT 'RESERVA_LEGAL'  FROM DUAL WHERE EXISTS
                      (SELECT 1 FROM RESERVA_LEGAL    x JOIN alvo a ON a.id = x.arq_bruto_id)
                UNION ALL SELECT 'APP'            FROM DUAL WHERE EXISTS
                      (SELECT 1 FROM APP              x JOIN alvo a ON a.id = x.arq_bruto_id)
                UNION ALL SELECT 'VEGETACAO'      FROM DUAL WHERE EXISTS
                      (SELECT 1 FROM VEGETACAO_NATIVA x JOIN alvo a ON a.id = x.arq_bruto_id)
                UNION ALL SELECT 'EMBARGO'        FROM DUAL WHERE EXISTS
                      (SELECT 1 FROM EMBARGO          x JOIN alvo a ON a.id = x.arq_bruto_id)
                UNION ALL SELECT 'DESMATAMENTO'   FROM DUAL WHERE EXISTS
                      (SELECT 1 FROM DESMATAMENTO     x JOIN alvo a ON a.id = x.arq_bruto_id)
                UNION ALL SELECT 'AREA_PROTEGIDA' FROM DUAL WHERE EXISTS
                      (SELECT 1 FROM AREA_PROTEGIDA   x JOIN alvo a ON a.id = x.arq_bruto_id)
                UNION ALL SELECT 'FOCO_CALOR'     FROM DUAL WHERE EXISTS
                      (SELECT 1 FROM FOCO_CALOR       x JOIN alvo a ON a.id = x.arq_bruto_id)
                """, String.class, arquivoId);
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
