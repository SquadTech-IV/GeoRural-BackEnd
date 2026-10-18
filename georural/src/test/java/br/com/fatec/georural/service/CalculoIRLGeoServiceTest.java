package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.IRLResponse;
import br.com.fatec.georural.dto.response.indicador.ResultadoIRL;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.RegraCalculoRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculoIRLGeoServiceTest {

    @Mock ImovelRuralRepository imovelRuralRepository;
    @Mock ResultadoIndicadorRepository resultadoIndicadorRepository;
    @Mock IndicadorRepository indicadorRepository;
    @Mock RegraCalculoRepository regraCalculoRepository;
    @Mock CalculoIRLService calculoIRLService;
    @Mock ResultadoIndicadorService resultadoIndicadorService;

    @InjectMocks CalculoIRLGeoService service;

    private ImovelRural imovelExemplo() {
        ImovelRural i = new ImovelRural();
        i.setId(1L);
        i.setVersao(new Versao());
        return i;
    }

    private Indicador indicadorExemplo() {
        Indicador ind = new Indicador();
        ind.setId(20L);
        ind.setSigla("IRL");
        return ind;
    }

    private void stubBase(ResultadoIRL retornoCalculo) {
        when(imovelRuralRepository.findById(1L)).thenReturn(Optional.of(imovelExemplo()));
        when(indicadorRepository.findBySigla("IRL")).thenReturn(Optional.of(indicadorExemplo()));
        when(regraCalculoRepository.findByIndicadorAndVigente(any(), any())).thenReturn(Optional.of(new RegraCalculo()));
        // RL 15 ha (150000 m2), imovel 100 ha
        when(imovelRuralRepository.calcularAreaReservaLegalAgregada(1L))
                .thenReturn(new Object[]{new BigDecimal("150000"), new BigDecimal("100")});
        when(imovelRuralRepository.buscarReservaLegalGeoJson(1L)).thenReturn("{\"type\":\"Polygon\"}");
        when(calculoIRLService.calcular(any(), any())).thenReturn(retornoCalculo);
        when(resultadoIndicadorService.salvarComoVigente(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void calcular_deveMarcarRuim_quandoDeficit() {
        stubBase(new ResultadoIRL("v1", new BigDecimal("20"), new BigDecimal("15"),
                new BigDecimal("20"), new BigDecimal("-5")));

        IRLResponse r = service.calcularReservaLegal(1L);

        assertThat(r.getSituacao()).isEqualTo("ruim");
        assertThat(r.getValorPercentual()).isEqualByComparingTo("15");   // percentualRL
        assertThat(r.getValorHectares()).isEqualByComparingTo("-5");     // saldo (deficit)
        assertThat(r.getValorAbsoluto()).isEqualByComparingTo("15");     // area de RL em ha
        assertThat(r.getMinimoPercent()).isEqualByComparingTo("20");
        assertThat(r.getAreaMinimaHa()).isEqualByComparingTo("20");
        assertThat(r.getVersaoRegra()).isEqualTo("v1");
        assertThat(r.getIndicadorSigla()).isEqualTo("IRL");
    }

    @Test
    void calcular_deveMarcarOk_quandoExcedente() {
        stubBase(new ResultadoIRL("v1", new BigDecimal("20"), new BigDecimal("25"),
                new BigDecimal("20"), new BigDecimal("5")));

        IRLResponse r = service.calcularReservaLegal(1L);

        assertThat(r.getSituacao()).isEqualTo("ok");
        assertThat(r.getValorHectares()).isEqualByComparingTo("5");
    }

    @Test
    void calcular_deveLancarNaoEncontrado_quandoImovelNaoExiste() {
        when(imovelRuralRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calcularReservaLegal(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }
}