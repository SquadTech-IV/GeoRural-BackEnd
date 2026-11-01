package br.com.fatec.georural.service.pipeline.etapa;

import br.com.fatec.georural.service.pipeline.ContextoExecucao;
import br.com.fatec.georural.service.pipeline.Etapa;
import br.com.fatec.georural.service.pipeline.EtapaPipeline;
import br.com.fatec.georural.service.pipeline.ResultadoEtapa;
import br.com.fatec.georural.service.pipeline.calculo.CalculoOracle;
import br.com.fatec.georural.service.pipeline.calculo.ResolvedorVersaoService;
import br.com.fatec.georural.service.pipeline.tratamento.RegistroTransformacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// etapa 5 - QUALIDADE: agrega por municipio e fecha o retrato da versao
@Slf4j
@Component
@RequiredArgsConstructor
public class QualidadeEtapa implements EtapaPipeline {

    private final ResolvedorVersaoService resolvedorVersao;
    private final CalculoOracle oracle;
    private final RegistroTransformacaoService transformacoes;
    private final JdbcTemplate jdbc;

    @Override
    public Etapa etapa() {
        return Etapa.QUALIDADE;
    }

    @Override
    @Transactional
    public ResultadoEtapa executar(ContextoExecucao ctx) {
        Long execucaoId = exigir(ctx.getExecucaoIdAtual(), "execucaoIdAtual");
        Long versaoId = resolvedorVersao.versaoDaRodada(ctx);

        long resultados = contar(
                "SELECT COUNT(*) FROM RESULTADO_INDICADOR WHERE ver_id = ?", versaoId);
        if (resultados == 0) {
            return ResultadoEtapa.semFeicoes(
                    "A versao nao tem resultado de indicador. Rode o CALCULO antes da QUALIDADE.");
        }

        RegistroTransformacaoService.Trilha trilha = transformacoes.abrir();
        List<String> resumos = new ArrayList<>();
        long agregados = 0;

        for (String sigla : siglasComResultado(versaoId)) {
            long feitos = oracle.agregarMunicipio(versaoId, sigla);
            agregados += feitos;
            resumos.add(sigla + " em " + feitos + " municipio(s)");
            trilha.passo("AGREGACAO", "Agregacao municipal do " + sigla + ": "
                    + feitos + " municipio(s).", "RESULTADO_INDICADOR", "RESULTADO_MUNICIPIO");
        }

        long semMunicipio = contar("""
                SELECT COUNT(*) FROM RESULTADO_INDICADOR r
                  JOIN IMOVEL_RURAL i ON i.imo_id = r.imo_id
                 WHERE r.ver_id = ? AND i.mun_id IS NULL
                """, versaoId);

        long emQuarentena = contar("""
                SELECT COUNT(*) FROM QUARENTENA q
                  JOIN VALIDACAO v ON v.val_id = q.val_id
                 WHERE v.exe_id IN (SELECT exe_id FROM EXECUCAO_PIPELINE WHERE exe_dag_run_id = ?)
                """, ctx.getDagRunId());

        trilha.passo("VALIDACAO", "Retrato da versao: " + resultados + " resultado(s) de indicador, "
                + semMunicipio + " imovel(eis) sem municipio e " + emQuarentena
                + " registro(s) em quarentena nesta rodada.", "RESULTADO_INDICADOR", "-");
        transformacoes.gravar(execucaoId, trilha.passos());

        ctx.comParametro("resultadosDaVersao", String.valueOf(resultados));
        ctx.comParametro("quarentenaDaRodada", String.valueOf(emQuarentena));

        String mensagem = String.join("; ", resumos)
                + ". " + resultados + " resultado(s) na versao"
                + (semMunicipio > 0 ? ", " + semMunicipio + " imovel(eis) sem municipio" : "")
                + (emQuarentena > 0 ? ", " + emQuarentena + " em quarentena" : "")
                + ". A versao segue como RASCUNHO ate alguem publicar.";

        return ResultadoEtapa.de(resultados, 0, mensagem).comVersao(versaoId);
    }

    // indicadores que de fato tem resultado nesta versao
    private List<String> siglasComResultado(Long versaoId) {
        return jdbc.queryForList("""
                SELECT DISTINCT i.ind_sigla
                  FROM RESULTADO_INDICADOR r
                  JOIN INDICADOR i ON i.ind_id = r.ind_id
                 WHERE r.ver_id = ?
                 ORDER BY i.ind_sigla
                """, String.class, versaoId);
    }

    private long contar(String sql, Object parametro) {
        Long total = jdbc.queryForObject(sql, Long.class, parametro);
        return total == null ? 0 : total;
    }

    private Long exigir(Long valor, String nome) {
        if (valor == null) {
            throw new IllegalArgumentException("A etapa QUALIDADE precisa de " + nome + ".");
        }
        return valor;
    }
}
