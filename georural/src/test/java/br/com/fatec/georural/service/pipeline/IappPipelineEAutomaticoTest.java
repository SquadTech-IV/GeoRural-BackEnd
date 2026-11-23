package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.repository.*;
import br.com.fatec.georural.service.CalculoIAEService;
import br.com.fatec.georural.service.CalculoIRLGeoService;
import br.com.fatec.georural.service.IndicadorAutomaticoService;
import br.com.fatec.georural.service.pipeline.calculo.CalculoOracle;
import br.com.fatec.georural.service.pipeline.calculo.RegraVigenteService;
import br.com.fatec.georural.service.pipeline.calculo.ResolvedorVersaoService;
import br.com.fatec.georural.service.pipeline.etapa.CalculoEtapa;
import br.com.fatec.georural.service.pipeline.tratamento.RegistroTransformacaoService;
import br.com.fatec.georural.service.pipeline.validacao.AlvosValidacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IappPipelineEAutomaticoTest {

    private AlvosValidacaoService alvosService;
    private ResolvedorVersaoService resolvedorVersao;
    private RegraVigenteService regras;
    private CalculoOracle oracle;
    private RegistroTransformacaoService transformacoes;
    private CalculoEtapa calculoEtapa;

    private ArquivoBrutoRepository arquivoRepository;
    private ImovelRuralRepository imovelRepository;
    private ReservaLegalRepository reservaLegalRepository;
    private CalculoIRLGeoService calculoIRLService;
    private CalculoIAEService calculoIAEService;
    private IndicadorRepository indicadorRepository;
    private RegraCalculoRepository regraCalculoRepository;
    private IndicadorAutomaticoService automaticoService;

    @BeforeEach
    void setUp() {
        alvosService = mock(AlvosValidacaoService.class);
        resolvedorVersao = mock(ResolvedorVersaoService.class);
        regras = mock(RegraVigenteService.class);
        oracle = mock(CalculoOracle.class);
        transformacoes = mock(RegistroTransformacaoService.class);
        when(transformacoes.abrir()).thenReturn(new RegistroTransformacaoService.Trilha());

        calculoEtapa = new CalculoEtapa(
                alvosService,
                resolvedorVersao,
                regras,
                oracle,
                transformacoes,
                imovelRepository
        );

        arquivoRepository = mock(ArquivoBrutoRepository.class);
        imovelRepository = mock(ImovelRuralRepository.class);
        reservaLegalRepository = mock(ReservaLegalRepository.class);
        calculoIRLService = mock(CalculoIRLGeoService.class);
        calculoIAEService = mock(CalculoIAEService.class);
        indicadorRepository = mock(IndicadorRepository.class);
        regraCalculoRepository = mock(RegraCalculoRepository.class);

        automaticoService = new IndicadorAutomaticoService(
                arquivoRepository,
                imovelRepository,
                reservaLegalRepository,
                calculoIRLService,
                calculoIAEService,
                indicadorRepository,
                regraCalculoRepository,
                oracle
        );
    }

    @Test
    @DisplayName("CalculoEtapa deve calcular IAPP quando o conjunto for APP")
    void calculoEtapa_escopoApp_deveAcionarCalculoIapp() {
        ContextoExecucao ctx = ContextoExecucao.novo(2L, 50L, 1L);
        ctx.setExecucaoIdAtual(100L);

        when(resolvedorVersao.versaoDaRodada(ctx)).thenReturn(4L);
        when(alvosService.tipoConjunto(50L)).thenReturn("APP");
        when(oracle.imoveisNoEscopo(4L, "APP")).thenReturn(15L);
        when(regras.emUso(eq("IAPP"), isNull()))
                .thenReturn(new RegraVigenteService.RegraEmUso(
                        8L, "1.0", "IAPP = (APP_VEG / APP_TOTAL) * 100", Map.of()));
        when(oracle.calcularIapp(4L, 100L, 8L, "APP", 4L, null)).thenReturn(15L);

        ResultadoEtapa res = calculoEtapa.executar(ctx);

        assertTrue(res.mensagem().contains("IAPP 1.0 em 15 imovel(eis)"));
        verify(oracle).calcularIapp(4L, 100L, 8L, "APP", 4L, null);
        verify(transformacoes).gravar(eq(100L), any());
    }

    @Test
    @DisplayName("CalculoEtapa deve calcular ICV e IAPP quando o conjunto for VEGETACAO")
    void calculoEtapa_escopoVegetacao_deveAcionarIcvEIapp() {
        ContextoExecucao ctx = ContextoExecucao.novo(3L, 51L, 1L);
        ctx.setExecucaoIdAtual(101L);

        when(resolvedorVersao.versaoDaRodada(ctx)).thenReturn(5L);
        when(alvosService.tipoConjunto(51L)).thenReturn("VEGETACAO");
        when(oracle.imoveisNoEscopo(5L, "VEGETACAO")).thenReturn(10L);
        when(regras.emUso(eq("ICV"), isNull()))
                .thenReturn(new RegraVigenteService.RegraEmUso(3L, "1.0", "ICV", Map.of()));
        when(regras.emUso(eq("IAPP"), isNull()))
                .thenReturn(new RegraVigenteService.RegraEmUso(8L, "1.0", "IAPP", Map.of()));
        when(oracle.calcularIcv(5L, 101L, 3L, 20.0, "VEGETACAO")).thenReturn(10L);
        when(oracle.calcularIapp(5L, 101L, 8L, "VEGETACAO", null, 5L)).thenReturn(10L);

        ResultadoEtapa res = calculoEtapa.executar(ctx);

        assertTrue(res.mensagem().contains("ICV 1.0"));
        assertTrue(res.mensagem().contains("IAPP 1.0 em 10 imovel(eis)"));
        verify(oracle).calcularIcv(5L, 101L, 3L, 20.0, "VEGETACAO");
        verify(oracle).calcularIapp(5L, 101L, 8L, "VEGETACAO", null, 5L);
    }

    @Test
    @DisplayName("IndicadorAutomaticoService deve recalcular e publicar IAPP ao processar arquivo APP ou VEGETACAO")
    void indicadorAutomatico_arquivoApp_deveRecalcularEPublicarIapp() {
        TipoConjunto tipoApp = new TipoConjunto();
        tipoApp.setCodigo("APP");

        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setTipo(tipoApp);

        ArquivoBruto arquivo = new ArquivoBruto();
        arquivo.setId(77L);
        arquivo.setConjunto(conjunto);

        Versao versao = new Versao();
        versao.setId(3L);

        ImovelRural imovel = new ImovelRural();
        imovel.setId(10L);
        imovel.setVersao(versao);

        Indicador indIapp = new Indicador();
        indIapp.setId(5L);
        indIapp.setSigla("IAPP");

        RegraCalculo regraIapp = new RegraCalculo();
        regraIapp.setId(8L);
        regraIapp.setVigente("S");

        when(arquivoRepository.findById(77L)).thenReturn(Optional.of(arquivo));
        when(imovelRepository.findAll()).thenReturn(List.of(imovel));
        when(indicadorRepository.findBySigla("IAPP")).thenReturn(Optional.of(indIapp));
        when(regraCalculoRepository.findByIndicadorAndVigente(indIapp, "S")).thenReturn(Optional.of(regraIapp));
        when(oracle.calcularIapp(3L, null, 8L, "TODOS", null, null)).thenReturn(1L);

        automaticoService.recalcularAposProcessamento(77L);

        verify(oracle).calcularIapp(3L, null, 8L, "TODOS", null, null);
        verify(oracle).publicar(3L);
    }
}