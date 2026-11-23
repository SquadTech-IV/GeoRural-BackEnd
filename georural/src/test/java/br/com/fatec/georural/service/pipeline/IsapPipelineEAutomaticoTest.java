package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.repository.*;
import br.com.fatec.georural.service.CalculoIAEService;
import br.com.fatec.georural.service.CalculoIRLGeoService;
import br.com.fatec.georural.service.IndicadorAutomaticoService;
import br.com.fatec.georural.service.pipeline.calculo.CalculoOracle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IsapPipelineEAutomaticoTest {

    @Test
    @DisplayName("IndicadorAutomaticoService recalcula ISAP no escopo IMOVEL e publica versao apos processamento de imoveis")
    void recalcularAposProcessamento_tipoImovel_deveChamarCalculoIsapEPublicar() {
        ArquivoBrutoRepository arquivoRepo = mock(ArquivoBrutoRepository.class);
        ImovelRuralRepository imovelRepo = mock(ImovelRuralRepository.class);
        ReservaLegalRepository rlRepo = mock(ReservaLegalRepository.class);
        CalculoIRLGeoService irlService = mock(CalculoIRLGeoService.class);
        CalculoIAEService iaeService = mock(CalculoIAEService.class);
        IndicadorRepository indicadorRepo = mock(IndicadorRepository.class);
        RegraCalculoRepository regraRepo = mock(RegraCalculoRepository.class);
        CalculoOracle calculoOracle = mock(CalculoOracle.class);

        TipoConjunto tipo = new TipoConjunto();
        tipo.setCodigo("IMOVEL");
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setTipo(tipo);
        ArquivoBruto arquivo = new ArquivoBruto();
        arquivo.setId(100L);
        arquivo.setConjunto(conjunto);

        Versao versao = new Versao();
        versao.setId(7L);
        ImovelRural imovel = new ImovelRural();
        imovel.setId(10L);
        imovel.setVersao(versao);

        Indicador isap = new Indicador();
        isap.setId(3L);
        isap.setSigla("ISAP");

        RegraCalculo regra = new RegraCalculo();
        regra.setId(15L);

        when(arquivoRepo.findById(100L)).thenReturn(Optional.of(arquivo));
        when(imovelRepo.findByArquivoBruto_Id(100L)).thenReturn(List.of(imovel));
        when(indicadorRepo.findBySigla("ISAP")).thenReturn(Optional.of(isap));
        when(regraRepo.findByIndicadorAndVigente(isap, "S")).thenReturn(Optional.of(regra));
        when(calculoOracle.calcularIsap(7L, null, 15L, "IMOVEL", null)).thenReturn(1L);

        IndicadorAutomaticoService service = new IndicadorAutomaticoService(
                arquivoRepo, imovelRepo, rlRepo, irlService, iaeService,
                indicadorRepo, regraRepo, calculoOracle
        );

        service.recalcularAposProcessamento(100L);

        verify(calculoOracle, times(1)).calcularIsap(7L, null, 15L, "IMOVEL", null);
        verify(calculoOracle, times(1)).publicar(7L);
    }

    @Test
    @DisplayName("IndicadorAutomaticoService recalcula ISAP no escopo TODOS quando nova camada AREA_PROTEGIDA e processada")
    void recalcularAposProcessamento_tipoAreaProtegida_deveChamarCalculoIsapEscopoTodos() {
        ArquivoBrutoRepository arquivoRepo = mock(ArquivoBrutoRepository.class);
        ImovelRuralRepository imovelRepo = mock(ImovelRuralRepository.class);
        ReservaLegalRepository rlRepo = mock(ReservaLegalRepository.class);
        CalculoIRLGeoService irlService = mock(CalculoIRLGeoService.class);
        CalculoIAEService iaeService = mock(CalculoIAEService.class);
        IndicadorRepository indicadorRepo = mock(IndicadorRepository.class);
        RegraCalculoRepository regraRepo = mock(RegraCalculoRepository.class);
        CalculoOracle calculoOracle = mock(CalculoOracle.class);

        TipoConjunto tipo = new TipoConjunto();
        tipo.setCodigo("AREA_PROTEGIDA");
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setTipo(tipo);
        ArquivoBruto arquivo = new ArquivoBruto();
        arquivo.setId(200L);
        arquivo.setConjunto(conjunto);

        Versao versao = new Versao();
        versao.setId(9L);
        ImovelRural imovel = new ImovelRural();
        imovel.setId(10L);
        imovel.setVersao(versao);

        Indicador isap = new Indicador();
        isap.setId(3L);
        isap.setSigla("ISAP");

        RegraCalculo regra = new RegraCalculo();
        regra.setId(15L);

        when(arquivoRepo.findById(200L)).thenReturn(Optional.of(arquivo));
        when(imovelRepo.findAll()).thenReturn(List.of(imovel));
        when(indicadorRepo.findBySigla("ISAP")).thenReturn(Optional.of(isap));
        when(regraRepo.findByIndicadorAndVigente(isap, "S")).thenReturn(Optional.of(regra));
        when(calculoOracle.calcularIsap(9L, null, 15L, "TODOS", null)).thenReturn(5L);

        IndicadorAutomaticoService service = new IndicadorAutomaticoService(
                arquivoRepo, imovelRepo, rlRepo, irlService, iaeService,
                indicadorRepo, regraRepo, calculoOracle
        );

        service.recalcularAposProcessamento(200L);

        verify(calculoOracle, times(1)).calcularIsap(9L, null, 15L, "TODOS", null);
        verify(calculoOracle, times(1)).publicar(9L);
    }
}