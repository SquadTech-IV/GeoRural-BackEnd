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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// etapa 3 - TRATAMENTO: saneia, recorta, calcula area e carrega o destino
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
    private final JdbcTemplate jdbc;

    @Override
    public Etapa etapa() {
        return Etapa.TRATAMENTO;
    }

    // transacao unica: as chamadas do PKG precisam da mesma conexao
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

        List<Camada> camadas = camadasDaStaging(stagingId, arquivoId);
        Long validacaoId = quarentena.abrirValidacao(arquivoId, execucaoId);
        double tolerancia = toleranciaArea(ctx);
        String uf = parametro(ctx, "uf", UF_PADRAO);

        RegistroTransformacaoService.Trilha trilha = transformacoes.abrir();
        long rejeitadas = 0;

        long corrigidas = oracle.sanear(stagingId);
        trilha.passo("VALIDACAO", "Saneamento com SDO_UTIL.RECTIFY_GEOMETRY: "
                + corrigidas + " geometria(s) corrigida(s).", "FEICAO_STAGING", "FEICAO_STAGING");

        long invalidas = oracle.rejeitarInvalidas(stagingId, validacaoId);
        rejeitadas += invalidas;
        trilha.passo("VALIDACAO", "Geometrias invalidas depois do saneamento: "
                + invalidas + " rejeitada(s).", "FEICAO_STAGING", "QUARENTENA");

        rejeitadas += recortar(camadas, stagingId, validacaoId, uf, trilha);

        long comArea = oracle.calcularAreas(stagingId);
        trilha.passo("CALCULO", "Area geodesica em hectares com SDO_GEOM.SDO_AREA: "
                + comArea + " feicao(oes).", "FEICAO_STAGING", "FEICAO_STAGING");

        rejeitadas += conferirAreas(camadas, stagingId, validacaoId, tolerancia, trilha);

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

        List<String> cargas = carregar(camadas, stagingId, versao.id(), trilha);
        exportar(camadas, conjuntoId, versao, arquivoId, execucaoId, cargas, trilha);

        long apagadas = cargaStaging.limpar(stagingId);
        trilha.passo("OUTRO", "Area de pouso liberada: " + apagadas + " linha(s).",
                "FEICAO_STAGING", "-");

        quarentena.fecharValidacao(validacaoId, recebidas, aprovadas, rejeitadas);
        ctx.comParametro("toleranciaAreaPct", String.valueOf(tolerancia));
        ctx.comParametro("uf", uf);
        ctx.comParametro("impressaoTratado", impressao);
        ctx.comParametro("perfis", perfisUsados(camadas));

        trilha.passo("OUTRO", versao.nova()
                        ? "Versao v" + versao.numero() + " criada como RASCUNHO."
                        : "Conteudo identico ao da versao v" + versao.numero()
                        + ": nenhuma versao nova foi criada.",
                "FEICAO_STAGING", "VERSAO");
        transformacoes.gravar(execucaoId, trilha.passos());

        String mensagem = String.join("; ", cargas)
                + (versao.nova()
                ? ". Versao v" + versao.numero() + " (RASCUNHO)"
                : ". Nada mudou: segue na versao v" + versao.numero());

        return ResultadoEtapa.de(recebidas, rejeitadas, mensagem).comVersao(versao.id());
    }

    // recorte e por camada: a malha municipal nao e recortada, ela e a base
    private long recortar(List<Camada> camadas, Long stagingId, Long validacaoId,
                          String uf, RegistroTransformacaoService.Trilha trilha) {
        long dispensadas = 0;
        long recortadas = 0;
        boolean algumRecortou = false;

        for (Camada camada : camadas) {
            if (!camada.perfil().exigeDentroDoParana()) {
                trilha.passo("OUTRO", "Camada " + camada.nome() + " ("
                                + camada.perfil().tipoConjunto() + ") nao e recortada: e base territorial.",
                        "FEICAO_STAGING", "FEICAO_STAGING");
                continue;
            }
            TratamentoOracle.Recorte recorte = oracle.recortarUf(stagingId, uf, camada.arquivoId());
            dispensadas += recorte.dispensadas();
            recortadas += recorte.recortadas();
            algumRecortou = true;
        }

        if (!algumRecortou) {
            return 0;
        }

        trilha.passo("RECORTE", "Recorte pelo limite de " + uf + ": " + dispensadas
                        + " feicao(oes) ja estavam inteiramente dentro e dispensaram o corte; "
                        + recortadas + " cruzavam a fronteira e passaram pelo SDO_GEOM.SDO_INTERSECTION.",
                "LIMITE_TERRITORIAL", "FEICAO_STAGING");

        long fora = oracle.rejeitarForaDaUf(stagingId, validacaoId, uf);
        trilha.passo("VALIDACAO", "Feicoes inteiramente fora de " + uf + ": "
                + fora + " rejeitada(s).", "FEICAO_STAGING", "QUARENTENA");
        return fora;
    }

    // area zero e area divergente so fazem sentido em camada de poligono
    private long conferirAreas(List<Camada> camadas, Long stagingId, Long validacaoId,
                               double tolerancia, RegistroTransformacaoService.Trilha trilha) {
        long rejeitadas = 0;

        for (Camada camada : camadas) {
            if (camada.perfil().geometriaEsperada() == TipoGeometria.POLIGONO) {
                long areaNula = oracle.rejeitarAreaNula(stagingId, validacaoId, camada.arquivoId());
                rejeitadas += areaNula;

                long avisos = oracle.avisarAreaIncompativel(
                        stagingId, validacaoId, tolerancia, camada.arquivoId());

                trilha.passo("VALIDACAO", camada.nome() + ": " + areaNula
                        + " com area zero ou negativa (rejeitada) e " + avisos
                        + " com area declarada divergente acima de " + tolerancia
                        + "% (aviso).", "FEICAO_STAGING", "QUARENTENA");
            }

            if ("RESERVA_LEGAL".equals(camada.perfil().tipoConjunto())) {
                long semImovel = oracle.rejeitarRlSemImovel(
                        stagingId, validacaoId, camada.arquivoId());
                rejeitadas += semImovel;
                trilha.passo("VALIDACAO", camada.nome() + ": " + semImovel
                                + " reserva(s) legal(is) cujo CAR nao esta em IMOVEL_RURAL.",
                        "IMOVEL_RURAL", "QUARENTENA");
            }
        }
        return rejeitadas;
    }

    // carga na ordem do perfil: imovel antes de reserva legal, sempre
    private List<String> carregar(List<Camada> camadas, Long stagingId, Long versaoId,
                                  RegistroTransformacaoService.Trilha trilha) {
        List<String> resumo = new ArrayList<>();

        for (Camada camada : camadas) {
            Long arq = camada.arquivoId();
            String tipo = camada.perfil().tipoConjunto();

            switch (tipo) {
                case "IMOVEL" -> {
                    long gravadas = oracle.carregarImovel(stagingId, versaoId, arq);
                    resumo.add(gravadas + " imovel(eis)");
                    trilha.passo("PADRONIZACAO", camada.nome()
                            + ": carga em IMOVEL_RURAL (MERGE por codigo do CAR), "
                            + gravadas + " registro(s).", "FEICAO_STAGING", "IMOVEL_RURAL");

                    TratamentoOracle.Vinculo vinculo =
                            oracle.vincularMunicipio(stagingId, versaoId, arq);
                    resumo.add(vinculo.total() + " vinculado(s) a municipio");
                    trilha.passo("CRUZAMENTO", "Vinculo com municipio: " + vinculo.porCodigo()
                            + " pelo codigo declarado e " + vinculo.porGeometria()
                            + " por cruzamento espacial.", "MUNICIPIO", "IMOVEL_RURAL");
                }
                case "VEGETACAO" -> {
                    TratamentoOracle.Carga carga =
                            oracle.carregarVegetacao(stagingId, versaoId, arq);
                    resumo.add(carga.gravadas() + " poligono(s) de vegetacao nativa");
                    trilha.passo("PADRONIZACAO", camada.nome()
                            + ": carga em VEGETACAO_NATIVA, " + carga.gravadas()
                            + " registro(s).", "FEICAO_STAGING", "VEGETACAO_NATIVA");

                    if (carga.ignoradas() > 0) {
                        resumo.add(carga.ignoradas() + " de outra classe de cobertura");
                        trilha.passo("VALIDACAO", camada.nome() + ": " + carga.ignoradas()
                                        + " poligono(s) fora do ICV por nao serem vegetacao nativa"
                                        + " (area consolidada, area nao classificada e afins).",
                                "FEICAO_STAGING", "-");
                    }
                }
                case "RESERVA_LEGAL" -> {
                    long gravadas = oracle.carregarReservaLegal(stagingId, versaoId, arq);
                    resumo.add(gravadas + " reserva(s) legal(is)");
                    trilha.passo("PADRONIZACAO", camada.nome()
                            + ": carga em RESERVA_LEGAL ligada ao imovel pelo CAR, "
                            + gravadas + " registro(s).", "FEICAO_STAGING", "RESERVA_LEGAL");
                }
                case "EMBARGO" -> {
                    long gravadas = oracle.carregarEmbargo(stagingId, versaoId, arq);
                    resumo.add(gravadas + " embargo(s)");
                    trilha.passo("PADRONIZACAO", camada.nome() + ": carga em EMBARGO, "
                            + gravadas + " registro(s).", "FEICAO_STAGING", "EMBARGO");
                }
                case "MALHA_MUNICIPAL" -> {
                    long gravadas = oracle.carregarMunicipio(stagingId, arq);
                    resumo.add(gravadas + " municipio(s)");
                    trilha.passo("PADRONIZACAO", camada.nome()
                            + ": carga em MUNICIPIO (MERGE por codigo do IBGE), "
                            + gravadas + " registro(s).", "FEICAO_STAGING", "MUNICIPIO");
                }
                default -> throw new IllegalStateException(
                        "Perfil " + tipo + " nao tem carga definida no TRATAMENTO.");
            }
        }
        return resumo;
    }

    // uma exportacao por perfil presente na rodada
    private void exportar(List<Camada> camadas, Long conjuntoId,
                          VersaoTratamentoService.VersaoResolvida versao, Long arquivoId,
                          Long execucaoId, List<String> cargas,
                          RegistroTransformacaoService.Trilha trilha) {
        Set<String> perfis = new LinkedHashSet<>();
        camadas.forEach(c -> perfis.add(c.perfil().tipoConjunto()));

        for (String tipo : perfis) {
            ExportacaoZonaTratadaService.Resultado tratada = exportacao.exportar(
                    conjuntoId, versao.id(), versao.numero(), tipo, arquivoId, execucaoId);
            if (tratada.exportou()) {
                cargas.add(tratada.feicoes() + " feicao(oes) de " + tipo + " na zona TRATADA");
                trilha.passo("OUTRO", "Zona TRATADA: " + tratada.mensagem() + " (sha256 "
                        + tratada.hash().substring(0, 12) + ").", tipo, "ZONA TRATADA");
            }
        }
    }

    // camadas que estao na area de pouso, cada uma com o perfil dela
    private List<Camada> camadasDaStaging(Long stagingId, Long arquivoId) {
        List<Object[]> linhas = jdbc.query(
                "SELECT DISTINCT s.stg_arq_bruto_id, NVL(a.arq_nome, 'sem nome') AS nome "
                        + "FROM FEICAO_STAGING s "
                        + "LEFT JOIN ARQUIVO_BRUTO a ON a.arq_bruto_id = s.stg_arq_bruto_id "
                        + "WHERE s.exe_id = ?",
                (rs, i) -> new Object[] {
                        rs.getObject("stg_arq_bruto_id") == null ? null : rs.getLong("stg_arq_bruto_id"),
                        rs.getString("nome") },
                stagingId);

        Optional<PerfilCamada> doConjunto =
                registroPerfis.buscar(alvosService.tipoConjunto(arquivoId));

        List<Camada> camadas = new ArrayList<>();
        for (Object[] linha : linhas) {
            String nome = (String) linha[1];
            PerfilCamada perfil = registroPerfis.porNomeDeCamada(nome)
                    .or(() -> doConjunto)
                    .orElseThrow(() -> new RegistroPerfis.PerfilNaoDefinidoException(
                            alvosService.tipoConjunto(arquivoId)));
            camadas.add(new Camada((Long) linha[0], nome, perfil));
        }

        camadas.sort(Comparator.comparingInt(c -> c.perfil().ordemNoTratamento()));
        log.info("Tratamento de {} camada(s), nesta ordem: {}", camadas.size(),
                camadas.stream().map(c -> c.nome() + " (" + c.perfil().tipoConjunto() + ")").toList());
        return camadas;
    }

    private String perfisUsados(List<Camada> camadas) {
        Set<String> perfis = new LinkedHashSet<>();
        camadas.forEach(c -> perfis.add(c.perfil().tipoConjunto()));
        return String.join(",", perfis);
    }

    // acha a execucao da VALIDACAO que escreveu a staging, pelo dag_run_id
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

    private record Camada(Long arquivoId, String nome, PerfilCamada perfil) {
    }
}
