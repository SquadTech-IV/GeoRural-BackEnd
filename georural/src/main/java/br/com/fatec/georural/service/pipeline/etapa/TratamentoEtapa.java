package br.com.fatec.georural.service.pipeline.etapa;

import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.service.pipeline.ContextoExecucao;
import br.com.fatec.georural.service.pipeline.Etapa;
import br.com.fatec.georural.service.pipeline.EtapaPipeline;
import br.com.fatec.georural.service.pipeline.ResultadoEtapa;
import br.com.fatec.georural.service.pipeline.perfil.PerfilCamada;
import br.com.fatec.georural.service.pipeline.perfil.RegistroPerfis;
import br.com.fatec.georural.service.pipeline.perfil.TipoGeometria;
import br.com.fatec.georural.service.pipeline.tratamento.ExportacaoZonaTratadaService;
import br.com.fatec.georural.service.pipeline.tratamento.RegistroTransformacaoService;
import br.com.fatec.georural.service.pipeline.tratamento.TratamentoOracle;
import br.com.fatec.georural.service.pipeline.tratamento.VersaoTratamentoService;
import br.com.fatec.georural.service.pipeline.validacao.AlvosValidacaoService;
import br.com.fatec.georural.service.pipeline.validacao.CargaStagingService;
import br.com.fatec.georural.service.pipeline.validacao.RegistroQuarentenaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// etapa 3 - TRATAMENTO: saneia, recorta pelo limite da UF, calcula area e
// carrega nas tabelas definitivas. Tudo em SQL sobre o conjunto inteiro.
@Slf4j
@Component
@RequiredArgsConstructor
public class TratamentoEtapa implements EtapaPipeline {

    private static final double TOLERANCIA_AREA_PADRAO = 5.0;
    private static final String UF_PADRAO = "PR";

    private final ExecucaoPipelineRepository execucaoRepository;
    private final AlvosValidacaoService alvosService;
    private final RegistroPerfis registroPerfis;
    private final TratamentoOracle oracle;
    private final VersaoTratamentoService versaoService;
    private final RegistroQuarentenaService quarentena;
    private final CargaStagingService cargaStaging;
    private final RegistroTransformacaoService transformacoes;
    private final ExportacaoZonaTratadaService exportacao;

    @Override
    public Etapa etapa() {
        return Etapa.TRATAMENTO;
    }

    // transacao unica: as chamadas precisam compartilhar a mesma conexao, senao
    // o ALTER SESSION do preparar_sessao cai em outra conexao do pool
    @Override
    @Transactional
    public ResultadoEtapa executar(ContextoExecucao ctx) {
        Long execucaoId = exigir(ctx.getExecucaoIdAtual(), "execucaoIdAtual");
        Long arquivoId = exigir(ctx.getArquivoBrutoId(), "arquivoBrutoId");
        Long conjuntoId = ctx.getConjuntoId() != null
                ? ctx.getConjuntoId() : alvosService.conjuntoId(arquivoId);

        // tem que ser a primeira instrucao da transacao
        oracle.prepararSessao();

        Long stagingId = execucaoDaStaging(ctx);
        long recebidas = oracle.restantes(stagingId);
        if (recebidas == 0) {
            return ResultadoEtapa.semFeicoes(
                    "Nao ha feicoes na area de pouso. Rode a etapa VALIDACAO antes do TRATAMENTO.");
        }

        PerfilCamada perfil = registroPerfis.para(alvosService.tipoConjunto(arquivoId));
        Long validacaoId = quarentena.abrirValidacao(arquivoId, execucaoId);
        double tolerancia = toleranciaArea(ctx);
        String uf = parametro(ctx, "uf", UF_PADRAO);

        RegistroTransformacaoService.Trilha trilha = transformacoes.abrir();
        long rejeitadas = 0;

        long corrigidas = oracle.sanear(stagingId);
        trilha.passo("VALIDACAO", "Saneamento de geometria com SDO_UTIL.RECTIFY_GEOMETRY: "
                + corrigidas + " geometria(s) corrigida(s).", "FEICAO_STAGING", "FEICAO_STAGING");

        long invalidas = oracle.rejeitarInvalidas(stagingId, validacaoId);
        rejeitadas += invalidas;
        trilha.passo("VALIDACAO", "Geometrias que seguiram invalidas depois do saneamento: "
                + invalidas + " rejeitada(s).", "FEICAO_STAGING", "QUARENTENA");

        if (perfil.exigeDentroDoParana()) {
            long recortadas = oracle.recortarUf(stagingId, uf);
            trilha.passo("RECORTE", "Recorte pelo limite de " + uf
                    + " com SDO_GEOM.SDO_INTERSECTION: " + recortadas + " geometria(s) processada(s).",
                    "LIMITE_TERRITORIAL", "FEICAO_STAGING");

            long fora = oracle.rejeitarForaDaUf(stagingId, validacaoId, uf);
            rejeitadas += fora;
            trilha.passo("VALIDACAO", "Feicoes inteiramente fora de " + uf + ": "
                    + fora + " rejeitada(s).", "FEICAO_STAGING", "QUARENTENA");
        } else {
            trilha.passo("OUTRO", "Camada " + perfil.rotulo()
                    + " nao e recortada: ela e a propria base territorial.",
                    "FEICAO_STAGING", "FEICAO_STAGING");
        }

        long comArea = oracle.calcularAreas(stagingId);
        trilha.passo("CALCULO", "Area geodesica em hectares com SDO_GEOM.SDO_AREA "
                + "(SRID 4674): " + comArea + " feicao(oes).", "FEICAO_STAGING", "FEICAO_STAGING");

        if (perfil.geometriaEsperada() == TipoGeometria.POLIGONO) {
            long areaNula = oracle.rejeitarAreaNula(stagingId, validacaoId);
            rejeitadas += areaNula;
            trilha.passo("VALIDACAO", "Feicoes com area zero ou negativa: "
                    + areaNula + " rejeitada(s).", "FEICAO_STAGING", "QUARENTENA");

            long avisos = oracle.avisarAreaIncompativel(stagingId, validacaoId, tolerancia);
            trilha.passo("VALIDACAO", "Area declarada divergente da calculada acima de "
                    + tolerancia + "%: " + avisos + " aviso(s). As feicoes seguem no fluxo.",
                    "FEICAO_STAGING", "QUARENTENA");
        }

        if ("RESERVA_LEGAL".equals(perfil.tipoConjunto())) {
            long semImovel = oracle.rejeitarRlSemImovel(stagingId, validacaoId);
            rejeitadas += semImovel;
            trilha.passo("VALIDACAO", "Reservas legais cujo CAR nao esta em IMOVEL_RURAL: "
                    + semImovel + " rejeitada(s).", "IMOVEL_RURAL", "QUARENTENA");
        }

        long aprovadas = oracle.restantes(stagingId);
        if (aprovadas == 0) {
            quarentena.fecharValidacao(validacaoId, recebidas, 0, rejeitadas);
            transformacoes.gravar(execucaoId, trilha.passos());
            return ResultadoEtapa.de(recebidas, rejeitadas,
                    "Nenhuma feicao passou pelo tratamento. Confira a quarentena: "
                            + rejeitadas + " rejeitada(s) de " + recebidas + ".");
        }

        String impressao = oracle.impressao(stagingId);
        VersaoTratamentoService.VersaoResolvida versao = versaoService.resolver(
                conjuntoId, execucaoId, ctx.getUsuarioId(), ctx.getCompetencia(), impressao);

        List<String> cargas = carregar(perfil, stagingId, versao.id(), trilha);

        // zona TRATADA: a camada tratada sai como GeoJSON no data lake
        ExportacaoZonaTratadaService.Resultado tratada = exportacao.exportar(
                conjuntoId, versao.id(), versao.numero(), perfil.tipoConjunto(),
                arquivoId, execucaoId);
        if (tratada.exportou()) {
            cargas.add(tratada.feicoes() + " feicao(oes) na zona TRATADA");
            trilha.passo("OUTRO", "Zona TRATADA: " + tratada.mensagem() + " (sha256 "
                    + tratada.hash().substring(0, 12) + ").", "IMOVEL_RURAL/EMBARGO", "ZONA TRATADA");
        }

        quarentena.fecharValidacao(validacaoId, recebidas, aprovadas, rejeitadas);
        ctx.comParametro("toleranciaAreaPct", String.valueOf(tolerancia));
        ctx.comParametro("uf", uf);
        ctx.comParametro("impressaoTratado", impressao);

        // a area de pouso ja cumpriu o papel; o rollback desfaz isso junto se algo falhar
        long apagadas = cargaStaging.limpar(stagingId);
        trilha.passo("OUTRO", "Area de pouso liberada: " + apagadas + " linha(s) de FEICAO_STAGING.",
                "FEICAO_STAGING", "-");

        trilha.passo("OUTRO", versao.nova()
                ? "Versao v" + versao.numero() + " criada como RASCUNHO."
                : "Conteudo identico ao da versao v" + versao.numero()
                        + ": nenhuma versao nova foi criada.",
                "FEICAO_STAGING", "VERSAO");
        transformacoes.gravar(execucaoId, trilha.passos());

        String mensagem = perfil.rotulo() + " - " + String.join("; ", cargas)
                + (versao.nova()
                   ? ". Versao v" + versao.numero() + " (RASCUNHO)"
                   : ". Nada mudou: segue na versao v" + versao.numero());

        return ResultadoEtapa.de(recebidas, rejeitadas, mensagem).comVersao(versao.id());
    }

    // chama a carga da camada correspondente ao perfil
    private List<String> carregar(PerfilCamada perfil, Long stagingId, Long versaoId,
                                  RegistroTransformacaoService.Trilha trilha) {
        List<String> resumo = new ArrayList<>();

        switch (perfil.tipoConjunto()) {
            case "IMOVEL" -> {
                long gravadas = oracle.carregarImovel(stagingId, versaoId);
                resumo.add(gravadas + " imovel(eis) em IMOVEL_RURAL");
                trilha.passo("PADRONIZACAO", "Carga em IMOVEL_RURAL (MERGE por codigo do CAR): "
                        + gravadas + " registro(s).", "FEICAO_STAGING", "IMOVEL_RURAL");

                TratamentoOracle.Vinculo vinculo = oracle.vincularMunicipio(stagingId, versaoId);
                resumo.add(vinculo.total() + " vinculado(s) a municipio");
                trilha.passo("CRUZAMENTO", "Vinculo com municipio: " + vinculo.porCodigo()
                        + " pelo codigo declarado e " + vinculo.porGeometria()
                        + " por cruzamento espacial.", "MUNICIPIO", "IMOVEL_RURAL");
            }
            case "RESERVA_LEGAL" -> {
                long gravadas = oracle.carregarReservaLegal(stagingId, versaoId);
                resumo.add(gravadas + " reserva(s) legal(is) em RESERVA_LEGAL");
                trilha.passo("PADRONIZACAO", "Carga em RESERVA_LEGAL ligada ao imovel pelo CAR: "
                        + gravadas + " registro(s).", "FEICAO_STAGING", "RESERVA_LEGAL");
            }
            case "EMBARGO" -> {
                long gravadas = oracle.carregarEmbargo(stagingId, versaoId);
                resumo.add(gravadas + " embargo(s) em EMBARGO");
                trilha.passo("PADRONIZACAO", "Carga em EMBARGO: " + gravadas + " registro(s).",
                        "FEICAO_STAGING", "EMBARGO");
            }
            case "MALHA_MUNICIPAL" -> {
                long gravadas = oracle.carregarMunicipio(stagingId);
                resumo.add(gravadas + " municipio(s) em MUNICIPIO");
                trilha.passo("PADRONIZACAO", "Carga em MUNICIPIO (MERGE por codigo do IBGE): "
                        + gravadas + " registro(s).", "FEICAO_STAGING", "MUNICIPIO");
            }
            default -> throw new IllegalStateException(
                    "Perfil " + perfil.tipoConjunto() + " nao tem carga definida no TRATAMENTO.");
        }
        return resumo;
    }

    // a staging foi escrita pela execucao da VALIDACAO, que e outra linha de
    // EXECUCAO_PIPELINE; as duas se encontram pelo dag_run_id
    private Long execucaoDaStaging(ContextoExecucao ctx) {
        String informado = ctx.getParametros().get("execucaoStagingId");
        if (informado != null && !informado.isBlank()) {
            return Long.parseLong(informado.trim());
        }
        return execucaoRepository.findByDagRunIdOrderByInicioAsc(ctx.getDagRunId()).stream()
                .filter(e -> Etapa.VALIDACAO.name().equals(e.getEtapa()))
                .max(Comparator.comparing(ExecucaoPipeline::getId))
                .map(ExecucaoPipeline::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Nao foi encontrada uma execucao de VALIDACAO para o dag_run_id '"
                                + ctx.getDagRunId() + "'. Rode a VALIDACAO antes, ou informe o "
                                + "parametro execucaoStagingId."));
    }

    private double toleranciaArea(ContextoExecucao ctx) {
        String bruto = ctx.getParametros().get("toleranciaAreaPct");
        if (bruto == null || bruto.isBlank()) {
            return TOLERANCIA_AREA_PADRAO;
        }
        try {
            return Double.parseDouble(bruto.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            log.warn("toleranciaAreaPct invalida ('{}'); usando {}%.", bruto, TOLERANCIA_AREA_PADRAO);
            return TOLERANCIA_AREA_PADRAO;
        }
    }

    private String parametro(ContextoExecucao ctx, String nome, String padrao) {
        String valor = ctx.getParametros().get(nome);
        return valor == null || valor.isBlank() ? padrao : valor.trim();
    }

    private Long exigir(Long valor, String nome) {
        if (valor == null) {
            throw new IllegalArgumentException("A etapa TRATAMENTO precisa de " + nome + ".");
        }
        return valor;
    }
}
