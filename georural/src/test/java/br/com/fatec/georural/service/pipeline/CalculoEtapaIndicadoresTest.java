package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.service.pipeline.calculo.CalculoOracle;
import br.com.fatec.georural.service.pipeline.calculo.RegraVigenteService;
import br.com.fatec.georural.service.pipeline.calculo.ResolvedorPeriodoIfc;
import br.com.fatec.georural.service.pipeline.calculo.ResolvedorVersaoService;
import br.com.fatec.georural.service.pipeline.etapa.CalculoEtapa;
import br.com.fatec.georural.service.pipeline.tratamento.RegistroTransformacaoService;
import br.com.fatec.georural.service.pipeline.validacao.AlvosValidacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalculoEtapaIndicadoresTest {

    private AlvosValidacaoService alvosService;
    private ResolvedorVersaoService resolvedorVersao;
    private RegraVigenteService regras;
    private CalculoOracle oracle;
    private RegistroTransformacaoService transformacoes;
    private ImovelRuralRepository imovelRepository;
    private ResolvedorPeriodoIfc resolvedorPeriodo;
    private CalculoEtapa calculoEtapa;

    @BeforeEach
    void setUp() {
        alvosService = mock(AlvosValidacaoService.class);
        resolvedorVersao = mock(ResolvedorVersaoService.class);
        regras = mock(RegraVigenteService.class);
        oracle = mock(CalculoOracle.class);
        transformacoes = mock(RegistroTransformacaoService.class);
        imovelRepository = mock(ImovelRuralRepository.class);
        resolvedorPeriodo = mock(ResolvedorPeriodoIfc.class);

        when(transformacoes.abrir()).thenReturn(new RegistroTransformacaoService.Trilha());

        // o imovelRepository e mockado ANTES da construcao: na versao anterior deste
        // teste ele entrava nulo no CalculoEtapa e o escopo AREA_PROTEGIDA quebrava
        calculoEtapa = new CalculoEtapa(
                alvosService,
                resolvedorVersao,
                regras,
                oracle,
                transformacoes,
                imovelRepository,
                resolvedorPeriodo
        );
    }

    private void regraGenerica() {
        when(regras.emUso(anyString(), isNull())).thenAnswer(chamada ->
                new RegraVigenteService.RegraEmUso(
                        1L, "1.0", "formula de teste", Map.of()));
    }

    @Test
    @DisplayName("Uma camada so: conjunto APP aciona apenas o IAPP, fixando a versao da APP")
    void umaCamada_app_acionaApenasIapp() {
        ContextoExecucao ctx = ContextoExecucao.novo(2L, 50L, 1L);
        ctx.setExecucaoIdAtual(100L);

        when(resolvedorVersao.versaoDaRodada(ctx)).thenReturn(4L);
        when(oracle.camadasDoArquivo(50L)).thenReturn(List.of());
        when(alvosService.tipoConjunto(50L)).thenReturn("APP");
        when(oracle.imoveisNoEscopo(4L, "APP")).thenReturn(15L);
        when(regras.emUso(eq("IAPP"), isNull()))
                .thenReturn(new RegraVigenteService.RegraEmUso(
                        8L, "1.0", "IAPP = (APP_VEG / APP_TOTAL) * 100", Map.of()));
        when(oracle.calcularIapp(4L, 100L, 8L, "APP", 4L, null)).thenReturn(15L);

        ResultadoEtapa res = calculoEtapa.executar(ctx);

        assertTrue(res.mensagem().contains("IAPP 1.0 em 15 imovel(eis)"));
        verify(oracle).calcularIapp(4L, 100L, 8L, "APP", 4L, null);
        verify(oracle, never()).calcularIrl(anyLong(), anyLong(), anyLong(), any(Double.class), anyString());
    }

    @Test
    @DisplayName("Uma camada so: conjunto VEGETACAO aciona ICV e IAPP, fixando a versao da vegetacao")
    void umaCamada_vegetacao_acionaIcvEIapp() {
        ContextoExecucao ctx = ContextoExecucao.novo(2L, 51L, 1L);
        ctx.setExecucaoIdAtual(101L);

        when(resolvedorVersao.versaoDaRodada(ctx)).thenReturn(5L);
        when(oracle.camadasDoArquivo(51L)).thenReturn(List.of());
        when(alvosService.tipoConjunto(51L)).thenReturn("VEGETACAO");
        when(oracle.imoveisNoEscopo(5L, "VEGETACAO")).thenReturn(10L);
        regraGenerica();
        when(oracle.calcularIcv(eq(5L), eq(101L), eq(1L), any(Double.class), eq("VEGETACAO")))
                .thenReturn(10L);
        when(oracle.calcularIapp(5L, 101L, 1L, "VEGETACAO", null, 5L)).thenReturn(10L);

        ResultadoEtapa res = calculoEtapa.executar(ctx);

        assertTrue(res.mensagem().contains("ICV"));
        assertTrue(res.mensagem().contains("IAPP"));
        verify(oracle).calcularIapp(5L, 101L, 1L, "VEGETACAO", null, 5L);
    }

    @Test
    @DisplayName("Uma camada so: conjunto AREA_PROTEGIDA aciona o ISAP e conta todos os imoveis")
    void umaCamada_areaProtegida_acionaIsap() {
        ContextoExecucao ctx = ContextoExecucao.novo(2L, 52L, 1L);
        ctx.setExecucaoIdAtual(102L);

        when(resolvedorVersao.versaoDaRodada(ctx)).thenReturn(6L);
        when(oracle.camadasDoArquivo(52L)).thenReturn(List.of());
        when(alvosService.tipoConjunto(52L)).thenReturn("AREA_PROTEGIDA");
        when(imovelRepository.count()).thenReturn(20L);
        regraGenerica();
        when(oracle.calcularIsap(6L, 102L, 1L, "AREA_PROTEGIDA", 6L)).thenReturn(20L);

        ResultadoEtapa res = calculoEtapa.executar(ctx);

        assertTrue(res.mensagem().contains("ISAP"));
        verify(oracle).calcularIsap(6L, 102L, 1L, "AREA_PROTEGIDA", 6L);
        verify(imovelRepository).count();
    }

    @Test
    @DisplayName("Zip do CAR com varias camadas: o IAPP entra junto e o escopo vira IMOVEL")
    void variasCamadas_zipDoCar_incluiIappComEscopoImovel() {
        ContextoExecucao ctx = ContextoExecucao.novo(2L, 53L, 1L);
        ctx.setExecucaoIdAtual(103L);

        when(resolvedorVersao.versaoDaRodada(ctx)).thenReturn(7L);
        // o que o TRATAMENTO gravou: tres camadas num conjunto tipado IMOVEL
        when(oracle.camadasDoArquivo(53L)).thenReturn(List.of("IMOVEL", "APP", "VEGETACAO"));
        when(oracle.imoveisNoEscopo(7L, "IMOVEL")).thenReturn(1L);
        regraGenerica();

        ResultadoEtapa res = calculoEtapa.executar(ctx);

        // o IAPP e o que faltava: antes o escopo vinha do tipo do conjunto e ele nunca disparava
        verify(oracle).calcularIapp(7L, 103L, 1L, "IMOVEL", 7L, 7L);
        assertTrue(res.mensagem().contains("IAPP"));
        // e os cinco do IMOVEL continuam
        assertTrue(res.mensagem().contains("IRL"));
        assertTrue(res.mensagem().contains("IAE"));
        assertTrue(res.mensagem().contains("ICV"));
        assertTrue(res.mensagem().contains("ISAP"));
        assertTrue(res.mensagem().contains("IDESMAT"));
        // o IFC nao: sem dado de foco, zero foco viraria "nenhum foco", que nao e verdade
        assertFalse(res.mensagem().contains("IFC"));
        // o tipo declarado do conjunto nao e mais consultado quando o banco respondeu
        verify(alvosService, never()).tipoConjunto(anyLong());
    }

    @Test
    @DisplayName("IAPP e ISAP sao projetados na RESULTADO_INDICADOR, que e o que as telas leem")
    void aposCalcular_projetaIappEIsap() {
        ContextoExecucao ctx = ContextoExecucao.novo(2L, 54L, 1L);
        ctx.setExecucaoIdAtual(104L);

        when(resolvedorVersao.versaoDaRodada(ctx)).thenReturn(8L);
        when(oracle.camadasDoArquivo(54L)).thenReturn(List.of("IMOVEL", "APP"));
        when(oracle.imoveisNoEscopo(8L, "IMOVEL")).thenReturn(3L);
        regraGenerica();

        calculoEtapa.executar(ctx);

        verify(oracle).projetar(8L, 104L, "IAPP");
        verify(oracle).projetar(8L, 104L, "ISAP");
    }

    @Test
    @DisplayName("Camada sem indicador nenhum nao roda calculo: devolve sem feicoes")
    void camadaSemIndicador_naoRodaCalculo() {
        ContextoExecucao ctx = ContextoExecucao.novo(2L, 55L, 1L);
        ctx.setExecucaoIdAtual(105L);

        when(resolvedorVersao.versaoDaRodada(ctx)).thenReturn(9L);
        when(oracle.camadasDoArquivo(55L)).thenReturn(List.of());
        when(alvosService.tipoConjunto(55L)).thenReturn("MALHA_MUNICIPAL");

        ResultadoEtapa res = calculoEtapa.executar(ctx);

        assertTrue(res.mensagem().contains("base territorial"));
        verify(oracle, never()).projetar(anyLong(), anyLong(), anyString());
    }
}
