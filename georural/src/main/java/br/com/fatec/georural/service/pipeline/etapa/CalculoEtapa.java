package br.com.fatec.georural.service.pipeline.etapa;

import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.service.pipeline.ContextoExecucao;
import br.com.fatec.georural.service.pipeline.Etapa;
import br.com.fatec.georural.service.pipeline.EtapaPipeline;
import br.com.fatec.georural.service.pipeline.ResultadoEtapa;
import br.com.fatec.georural.service.pipeline.calculo.CalculoOracle;
import br.com.fatec.georural.service.pipeline.calculo.RegraVigenteService;
import br.com.fatec.georural.service.pipeline.calculo.ResolvedorPeriodoIfc;
import br.com.fatec.georural.service.pipeline.calculo.ResolvedorVersaoService;
import br.com.fatec.georural.service.pipeline.tratamento.RegistroTransformacaoService;
import br.com.fatec.georural.service.pipeline.validacao.AlvosValidacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CalculoEtapa implements EtapaPipeline {

    private static final double MINIMO_RL_PADRAO = 20.0;
    private static final double MINIMO_ICV_PADRAO = 20.0;
    private static final double FATOR_IFC_PADRAO = 1000.0;
    private static final double MESES_IFC_PADRAO = 12.0;

    private final AlvosValidacaoService alvosService;
    private final ResolvedorVersaoService resolvedorVersao;
    private final RegraVigenteService regras;
    private final CalculoOracle oracle;
    private final RegistroTransformacaoService transformacoes;
    private final ImovelRuralRepository imovelRepository;
    private final ResolvedorPeriodoIfc resolvedorPeriodo;

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

        // um zip do CAR traz varias camadas num conjunto tipado IMOVEL: o que
        // recalcular sai do que o arquivo gravou, nao do tipo declarado
        Set<String> camadas = camadasTratadas(arquivoId);
        List<String> indicadores = indicadoresDasCamadas(camadas);
        String escopo = escopoBase(camadas);
        log.info("CALCULO do arquivo {}: camadas {} -> indicadores {}",
                arquivoId, camadas, indicadores);

        if (indicadores.isEmpty()) {
            return ResultadoEtapa.semFeicoes("Nenhuma camada deste arquivo "
                    + camadas + " alimenta indicador: e base territorial.");
        }

        long imoveis = "AREA_PROTEGIDA".equals(escopo)
                ? imovelRepository.count()
                : oracle.imoveisNoEscopo(versaoId, escopo);

        if (imoveis == 0) {
            return ResultadoEtapa.semFeicoes(
                    "Nenhum imovel alcancado pela versao. Rode o TRATAMENTO do imovel rural antes.");
        }

        RegistroTransformacaoService.Trilha trilha = transformacoes.abrir();
        List<String> resumos = new ArrayList<>();

        if (indicadores.contains("IRL")) {
            RegraVigenteService.RegraEmUso regra =
                    regras.emUso("IRL", regraForcada(ctx, "rcaIdIrl"));
            double minimo = regra.numero("minimo_rl_percent", MINIMO_RL_PADRAO);

            long feitos = oracle.calcularIrl(versaoId, execucaoId, regra.id(), minimo, escopo);
            resumos.add("IRL " + regra.versao() + " em " + feitos + " imovel(eis)");
            ctx.comParametro("regraIrl", regra.versao());
            ctx.comParametro("minimo_rl_percent", String.valueOf(minimo));
            trilha.passo("DISSOLVER", "IRL pela regra " + regra.versao() + " com minimo de "
                            + minimo + "%: a reserva legal de cada imovel e unida com"
                            + " SDO_AGGR_UNION e recortada pelo perimetro antes da medicao."
                            + " " + feitos + " resultado(s). Formula: " + regra.formula(),
                    "RESERVA_LEGAL + IMOVEL_RURAL", "RESULTADO_INDICADOR");
        }

        if (indicadores.contains("IAE")) {
            RegraVigenteService.RegraEmUso regra =
                    regras.emUso("IAE", regraForcada(ctx, "rcaIdIae"));

            long feitos = oracle.calcularIae(versaoId, execucaoId, regra.id(), escopo);
            resumos.add("IAE " + regra.versao() + " em " + feitos + " imovel(eis)");
            ctx.comParametro("regraIae", regra.versao());
            trilha.passo("DISSOLVER", "IAE pela regra " + regra.versao()
                            + " (embargos que tocam o imovel unidos com SDO_AGGR_UNION antes"
                            + " da medicao, para sobreposicao entre embargos nao contar duas"
                            + " vezes): " + feitos + " resultado(s).",
                    "EMBARGO + IMOVEL_RURAL", "RESULTADO_INDICADOR");
        }

        if (indicadores.contains("ICV")) {
            RegraVigenteService.RegraEmUso regra =
                    regras.emUso("ICV", regraForcada(ctx, "rcaIdIcv"));
            double minimo = regra.numero("minimo_cobertura_percent", MINIMO_ICV_PADRAO);

            long feitos = oracle.calcularIcv(versaoId, execucaoId, regra.id(), minimo, escopo);
            resumos.add("ICV " + regra.versao() + " em " + feitos + " imovel(eis)");
            ctx.comParametro("regraIcv", regra.versao());
            ctx.comParametro("minimo_cobertura_percent", String.valueOf(minimo));
            trilha.passo("DISSOLVER", "ICV pela regra " + regra.versao() + " com minimo de "
                            + minimo + "%: a vegetacao que toca cada imovel e unida com"
                            + " SDO_AGGR_UNION antes da medicao, para poligono sobreposto nao"
                            + " contar duas vezes. " + feitos + " resultado(s).",
                    "VEGETACAO_NATIVA + IMOVEL_RURAL", "RESULTADO_INDICADOR");
        }

        if (indicadores.contains("IAPP")) {
            RegraVigenteService.RegraEmUso regra =
                    regras.emUso("IAPP", regraForcada(ctx, "rcaIdIapp"));
            // so a camada que chegou nesta rodada fixa a versao; a outra o PKG_IAPP resolve
            Long appVersaoId = camadas.contains("APP") ? versaoId : null;
            Long vegVersaoId = camadas.contains("VEGETACAO") ? versaoId : null;

            long feitos = oracle.calcularIapp(versaoId, execucaoId, regra.id(), escopo,
                    appVersaoId, vegVersaoId);
            resumos.add("IAPP " + regra.versao() + " em " + feitos + " imovel(eis)");
            ctx.comParametro("regraIapp", regra.versao());
            trilha.passo("CRUZAMENTO", "IAPP pela regra " + regra.versao()
                            + " (APP dissolvida cruzada com a vegetacao dissolvida para medir"
                            + " o passivo de APP por imovel): " + feitos + " resultado(s).",
                    "APP + VEGETACAO_NATIVA + IMOVEL_RURAL", "RESULTADO_IAPP");

            // sem esta copia o IAPP calcula e nao aparece em consulta nenhuma.
            // O tipo e OUTRO porque CK_TRA_TIPO so aceita VALIDACAO, PADRONIZACAO,
            // REPROJECAO, RECORTE, DISSOLVER, CRUZAMENTO, CALCULO, AGREGACAO e OUTRO.
            // REPROJECAO e reprojecao de coordenadas, outra coisa: nao serve aqui.
            long projIapp = oracle.projetar(versaoId, execucaoId, "IAPP");
            trilha.passo("OUTRO", "IAPP copiado da RESULTADO_IAPP para a"
                            + " RESULTADO_INDICADOR, que e a tabela lida pelas telas"
                            + " e pelos endpoints de consulta: " + projIapp + " linha(s).",
                    "RESULTADO_IAPP", "RESULTADO_INDICADOR");
        }

        if (indicadores.contains("ISAP")) {
            RegraVigenteService.RegraEmUso regra =
                    regras.emUso("ISAP", regraForcada(ctx, "rcaIdIsap"));
            Long camadaVersaoId = camadas.contains("AREA_PROTEGIDA") ? versaoId : null;

            long feitos = oracle.calcularIsap(versaoId, execucaoId, regra.id(), escopo, camadaVersaoId);
            resumos.add("ISAP " + regra.versao() + " em " + feitos + " imovel(eis)");
            ctx.comParametro("regraIsap", regra.versao());
            trilha.passo("CRUZAMENTO", "ISAP pela regra " + regra.versao()
                            + " (sobreposicao imovel x areas protegidas com SDO_AGGR_UNION"
                            + " e detalhamento por area): " + feitos + " resultado(s).",
                    "AREA_PROTEGIDA + IMOVEL_RURAL", "RESULTADO_ISAP_RESUMO");

            // so a categoria TOTAL vira resultado: a RESULTADO_INDICADOR e unica
            // por (imovel, indicador, versao)
            long projIsap = oracle.projetar(versaoId, execucaoId, "ISAP");
            trilha.passo("OUTRO", "ISAP copiado da RESULTADO_ISAP_RESUMO"
                            + " (categoria TOTAL) para a RESULTADO_INDICADOR: "
                            + projIsap + " linha(s).",
                    "RESULTADO_ISAP_RESUMO", "RESULTADO_INDICADOR");
        }

        if (indicadores.contains("IDESMAT")) {
            RegraVigenteService.RegraEmUso regra =
                    regras.emUso("IDESMAT", regraForcada(ctx, "rcaIdIdesmat"));

            String m2008 = regra.parametros().getOrDefault("marco_2008", "2008-07-22");
            String m2019 = regra.parametros().getOrDefault("marco_2019", "2019-07-31");
            String m2020 = regra.parametros().getOrDefault("marco_2020", "2020-12-31");

            long feitos = oracle.calcularIdesmat(versaoId, execucaoId, regra.id(), escopo);
            resumos.add("IDESMAT " + regra.versao() + " em " + feitos + " imovel(eis)");
            ctx.comParametro("regraIdesmat", regra.versao());
            ctx.comParametro("marco_2008", m2008);
            ctx.comParametro("marco_2019", m2019);
            ctx.comParametro("marco_2020", m2020);
            trilha.passo("DISSOLVER", "IDESMAT pela regra " + regra.versao()
                            + " (cruzamento EPSG:4674, dissolucao SDO_AGGR_UNION e medicao de"
                            + " area no elipsoide SIRGAS 2000 apos "
                            + m2008 + ", " + m2019 + " e " + m2020 + "): "
                            + feitos + " resultado(s).",
                    "DESMATAMENTO + IMOVEL_RURAL", "RESULTADO_INDICADOR");
        }

        if (indicadores.contains("IFC")) {
            RegraVigenteService.RegraEmUso regra =
                    regras.emUso("IFC", regraForcada(ctx, "rcaIdIfc"));
            double fator = regra.numero("fator_area", FATOR_IFC_PADRAO);
            int meses = (int) regra.numero("periodo_meses", MESES_IFC_PADRAO);

            ResolvedorPeriodoIfc.Resolvido janela = resolvedorPeriodo.resolver(
                    versaoId, ctx.getCompetencia(),
                    ctx.getParametros().get("dataReferenciaIfc"), meses);

            long feitos = oracle.calcularIfc(versaoId, execucaoId, regra.id(),
                    janela.periodo().inicio(), janela.periodo().fim(), fator, escopo);
            resumos.add("IFC " + regra.versao() + " em " + feitos + " imovel(eis)");
            ctx.comParametro("regraIfc", regra.versao());
            ctx.comParametro("fator_area", String.valueOf(fator));
            ctx.comParametro("periodo_meses", String.valueOf(meses));
            ctx.comParametro("periodoIfc",
                    janela.periodo().inicio() + " a " + janela.periodo().fim());
            trilha.passo("CALCULO", "IFC pela regra " + regra.versao() + ": focos entre "
                            + janela.periodo().inicio() + " e " + janela.periodo().fim()
                            + " (" + meses + " meses, periodo vindo de " + janela.origem()
                            + ") por " + fator + " ha de imovel. " + feitos + " resultado(s).",
                    "FOCO_CALOR + IMOVEL_RURAL", "RESULTADO_INDICADOR");
        }

        transformacoes.gravar(execucaoId, trilha.passos());
        ctx.comParametro("escopoCalculo", escopo);
        ctx.comParametro("camadasCalculadas", String.join(",", camadas));

        return ResultadoEtapa.de(imoveis, 0,
                        String.join("; ", resumos) + ". Resultados gravados como NAO vigentes: "
                                + "ficam valendo quando a versao for publicada.")
                .comVersao(versaoId);
    }

    // Quais camadas este arquivo gravou. Sai do banco, e nao do tipo do conjunto
    // nem do contexto: o Airflow roda uma etapa por chamada de API, cada uma com
    // contexto novo, entao o que o TRATAMENTO anotou nao chega aqui nesse caminho.
    // O tipo do conjunto fica como ultimo recurso, para arquivo que nao gravou nada.
    private Set<String> camadasTratadas(Long arquivoId) {
        List<String> doBanco = oracle.camadasDoArquivo(arquivoId);
        if (!doBanco.isEmpty()) {
            return new LinkedHashSet<>(doBanco);
        }
        String tipo = alvosService.tipoConjunto(arquivoId);
        return tipo == null || tipo.isBlank()
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(List.of(tipo));
    }

    // a uniao do que cada camada obriga a recalcular, sem repetir indicador
    private List<String> indicadoresDasCamadas(Set<String> camadas) {
        Set<String> uniao = new LinkedHashSet<>();
        camadas.forEach(c -> uniao.addAll(indicadoresDaCamada(c)));
        return List.copyOf(uniao);
    }

    // Qual conjunto de imoveis cada calculo varre. Com uma camada so, e ela mesma.
    // Com varias (o zip do CAR), IMOVEL -- que no imoveis_no_escopo significa todos
    // os imoveis: com camadas misturadas o superconjunto e a unica escolha segura.
    private String escopoBase(Set<String> camadas) {
        if (camadas.size() == 1) {
            return camadas.iterator().next();
        }
        return camadas.isEmpty() ? "" : "IMOVEL";
    }

    // qual indicador cada camada recem-chegada obriga a recalcular
    private List<String> indicadoresDaCamada(String tipoConjunto) {
        return switch (tipoConjunto == null ? "" : tipoConjunto) {
            case "IMOVEL" -> List.of("IRL", "IAE", "ICV", "ISAP", "IDESMAT");
            case "RESERVA_LEGAL" -> List.of("IRL");
            case "EMBARGO" -> List.of("IAE");
            case "VEGETACAO" -> List.of("ICV", "IAPP");
            case "APP" -> List.of("IAPP");
            case "AREA_PROTEGIDA" -> List.of("ISAP");
            case "DESMATAMENTO" -> List.of("IDESMAT");
            case "FOCO_CALOR" -> List.of("IFC");
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
