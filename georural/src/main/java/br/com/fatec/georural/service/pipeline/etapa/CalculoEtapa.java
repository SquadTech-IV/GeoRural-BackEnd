package br.com.fatec.georural.service.pipeline.etapa;

import br.com.fatec.georural.service.pipeline.ContextoExecucao;
import br.com.fatec.georural.service.pipeline.Etapa;
import br.com.fatec.georural.service.pipeline.EtapaPipeline;
import br.com.fatec.georural.service.pipeline.ResultadoEtapa;
import br.com.fatec.georural.service.pipeline.calculo.CalculoOracle;
import br.com.fatec.georural.service.pipeline.calculo.RegraVigenteService;
import br.com.fatec.georural.service.pipeline.calculo.ResolvedorVersaoService;
import br.com.fatec.georural.service.pipeline.tratamento.RegistroTransformacaoService;
import br.com.fatec.georural.service.pipeline.validacao.AlvosValidacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// etapa 4 - CALCULO: IRL, IAE e ICV sobre os imoveis alcancados pela rodada
@Slf4j
@Component
@RequiredArgsConstructor
public class CalculoEtapa implements EtapaPipeline {

    private static final double MINIMO_RL_PADRAO = 20.0;
    private static final double MINIMO_ICV_PADRAO = 20.0;

    private final AlvosValidacaoService alvosService;
    private final ResolvedorVersaoService resolvedorVersao;
    private final RegraVigenteService regras;
    private final CalculoOracle oracle;
    private final RegistroTransformacaoService transformacoes;

    @Override
    public Etapa etapa() {
        return Etapa.CALCULO;
    }

    @Override
    @Transactional
    public ResultadoEtapa executar(ContextoExecucao ctx) {
        Long execucaoId = exigir(ctx.getExecucaoIdAtual(), "execucaoIdAtual");
        Long arquivoId = exigir(ctx.getArquivoBrutoId(), "arquivoBrutoId");
        Long versaoId = resolvedorVersao.versaoDaRodada(ctx);

        String escopo = alvosService.tipoConjunto(arquivoId);
        List<String> indicadores = indicadoresDoEscopo(escopo);
        if (indicadores.isEmpty()) {
            return ResultadoEtapa.semFeicoes("A camada " + escopo
                    + " nao alimenta indicador: ela e base territorial.");
        }

        long imoveis = oracle.imoveisNoEscopo(versaoId, escopo);
        if (imoveis == 0) {
            return ResultadoEtapa.semFeicoes(
                    "Nenhum imovel alcancado pela versao. Rode o TRATAMENTO do imovel rural antes.");
        }

        RegistroTransformacaoService.Trilha trilha = transformacoes.abrir();
        List<String> resumos = new ArrayList<>();
        long calculados = 0;

        if (indicadores.contains("IRL")) {
            RegraVigenteService.RegraEmUso regra =
                    regras.emUso("IRL", regraForcada(ctx, "rcaIdIrl"));
            double minimo = regra.numero("minimo_rl_percent", MINIMO_RL_PADRAO);

            long feitos = oracle.calcularIrl(versaoId, execucaoId, regra.id(), minimo, escopo);
            calculados += feitos;
            resumos.add("IRL " + regra.versao() + " em " + feitos + " imovel(eis)");
            ctx.comParametro("regraIrl", regra.versao());
            ctx.comParametro("minimo_rl_percent", String.valueOf(minimo));
            trilha.passo("CALCULO", "IRL pela regra " + regra.versao() + " com minimo de "
                            + minimo + "%: " + feitos + " resultado(s). Formula: " + regra.formula(),
                    "RESERVA_LEGAL + IMOVEL_RURAL", "RESULTADO_INDICADOR");
        }

        if (indicadores.contains("IAE")) {
            RegraVigenteService.RegraEmUso regra =
                    regras.emUso("IAE", regraForcada(ctx, "rcaIdIae"));

            long feitos = oracle.calcularIae(versaoId, execucaoId, regra.id(), escopo);
            calculados += feitos;
            resumos.add("IAE " + regra.versao() + " em " + feitos + " imovel(eis)");
            ctx.comParametro("regraIae", regra.versao());
            trilha.passo("CRUZAMENTO", "IAE pela regra " + regra.versao()
                    + " (sobreposicao imovel x embargo com SDO_RELATE + SDO_INTERSECTION): "
                    + feitos + " resultado(s).", "EMBARGO + IMOVEL_RURAL", "RESULTADO_INDICADOR");
        }

        if (indicadores.contains("ICV")) {
            RegraVigenteService.RegraEmUso regra =
                    regras.emUso("ICV", regraForcada(ctx, "rcaIdIcv"));
            double minimo = regra.numero("minimo_cobertura_percent", MINIMO_ICV_PADRAO);

            long feitos = oracle.calcularIcv(versaoId, execucaoId, regra.id(), minimo, escopo);
            calculados += feitos;
            resumos.add("ICV " + regra.versao() + " em " + feitos + " imovel(eis)");
            ctx.comParametro("regraIcv", regra.versao());
            ctx.comParametro("minimo_cobertura_percent", String.valueOf(minimo));
            trilha.passo("DISSOLVER", "ICV pela regra " + regra.versao() + " com minimo de "
                            + minimo + "%: a vegetacao que toca cada imovel e unida com"
                            + " SDO_AGGR_UNION antes da medicao, para poligono sobreposto nao"
                            + " contar duas vezes. " + feitos + " resultado(s).",
                    "VEGETACAO_NATIVA + IMOVEL_RURAL", "RESULTADO_INDICADOR");
        }

        transformacoes.gravar(execucaoId, trilha.passos());
        ctx.comParametro("escopoCalculo", escopo);

        return ResultadoEtapa.de(imoveis, 0,
                        String.join("; ", resumos) + ". Resultados gravados como NAO vigentes: "
                                + "ficam valendo quando a versao for publicada.")
                .comVersao(versaoId);
    }
    
    private List<String> indicadoresDoEscopo(String tipoConjunto) {
        return switch (tipoConjunto == null ? "" : tipoConjunto) {
            case "IMOVEL" -> List.of("IRL", "IAE", "ICV");
            case "RESERVA_LEGAL" -> List.of("IRL");
            case "EMBARGO" -> List.of("IAE");
            case "VEGETACAO" -> List.of("ICV");
            default -> List.of();
        };
    }

    // permite recalcular com uma versao antiga de regra
    private Long regraForcada(ContextoExecucao ctx, String nome) {
        String valor = ctx.getParametros().get(nome);
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(valor.trim());
        } catch (NumberFormatException e) {
            log.warn("{} invalido ('{}'); usando a regra vigente.", nome, valor);
            return null;
        }
    }

    private Long exigir(Long valor, String nome) {
        if (valor == null) {
            throw new IllegalArgumentException("A etapa CALCULO precisa de " + nome + ".");
        }
        return valor;
    }
}