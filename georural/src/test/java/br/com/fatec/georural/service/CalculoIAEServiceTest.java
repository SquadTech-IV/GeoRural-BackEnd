package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.mapper.IAEMapper;
import br.com.fatec.georural.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculoIAEServiceTest {

    @Mock ImovelRuralRepository imovelRuralRepository;
    @Mock IndicadorRepository indicadorRepository;
    @Mock RegraCalculoRepository regraCalculoRepository;
    @Mock ResultadoIndicadorRepository resultadoIndicadorRepository;
    @Mock VersaoEmbargoService versaoEmbargoService;
    @Mock IAEMapper mapper;

    @InjectMocks CalculoIAEService service;

    private ImovelRural imovelExemplo() {
        ImovelRural i = new ImovelRural();
        i.setId(1L);
        i.setAreaTotal(new BigDecimal("1472.88"));
        i.setVersao(new Versao());
        return i;
    }

    private Indicador indicadorExemplo() {
        Indicador ind = new Indicador();
        ind.setId(10L);
        ind.setSigla("IAE");
        return ind;
    }

    private void stubsBase() {
        when(imovelRuralRepository.findById(1L)).thenReturn(Optional.of(imovelExemplo()));
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.of(indicadorExemplo()));
        when(regraCalculoRepository.findByIndicadorAndVigente(any(), any()))
                .thenReturn(Optional.of(new RegraCalculo()));
        when(versaoEmbargoService.resolverId()).thenReturn(61L);
        when(resultadoIndicadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(imovelRuralRepository.encontrarEmbargosIntersectantes(1L, 61L)).thenReturn(List.of());
        when(mapper.toResponse(any(), any())).thenAnswer(inv -> {
            ResultadoIndicador r = inv.getArgument(0);
            return new IAEResponse(1L, "IAE", r.getValorPercentual(), r.getValorHectares(),
                    r.getValorAbsoluto(), r.getDataCalculo(), List.of());
        });
    }

    @Test
    void calcular_deveRetornarPercentualZero_quandoNaoHaEmbargo() {
        stubsBase();
        when(imovelRuralRepository.calcularAreaEmbargadaAgregada(1L, 61L))
                .thenReturn(new Object[]{null, new BigDecimal("1472.88")});

        IAEResponse resposta = service.calcularSobreposicaoEmbargo(1L);

        assertThat(resposta.getValorPercentual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resposta.getEmbargosIntersectantes()).isEmpty();
    }

    @Test
    void calcular_deveConverterM2ParaHectaresAoCalcularPercentual() {
        stubsBase();
        // 915030 m2 = 91,503 ha, sobre um imóvel de 1472,88 ha
        when(imovelRuralRepository.calcularAreaEmbargadaAgregada(1L, 61L))
                .thenReturn(new Object[]{new BigDecimal("915030"), new BigDecimal("1472.88")});

        IAEResponse resposta = service.calcularSobreposicaoEmbargo(1L);

        assertThat(resposta.getValorHectares()).isEqualByComparingTo("91.5030");
        assertThat(resposta.getValorPercentual().doubleValue()).isCloseTo(6.2125, within(0.001));
    }

    @Test
    void calcular_deveLancarConflito_quandoJaExisteResultado() {
        when(imovelRuralRepository.findById(1L)).thenReturn(Optional.of(imovelExemplo()));
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.of(indicadorExemplo()));
        when(regraCalculoRepository.findByIndicadorAndVigente(any(), any()))
                .thenReturn(Optional.of(new RegraCalculo()));
        when(resultadoIndicadorRepository.existsByImovelIdAndIndicadorAndVersao(any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.calcularSobreposicaoEmbargo(1L))
                .isInstanceOf(ConflitoException.class);

        verify(resultadoIndicadorRepository, never()).save(any());
    }

    @Test
    void calcular_deveLancarNaoEncontrado_quandoImovelNaoExiste() {
        when(imovelRuralRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calcularSobreposicaoEmbargo(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }

    @Test
    void calcular_deveLancarEstadoInconsistente_quandoIndicadorNaoCadastrado() {
        when(imovelRuralRepository.findById(1L)).thenReturn(Optional.of(imovelExemplo()));
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calcularSobreposicaoEmbargo(1L))
                .isInstanceOf(EstadoInconsistenteException.class);
    }

    @Test
    void calcular_deveLancarEstadoInconsistente_quandoSemRegraVigente() {
        when(imovelRuralRepository.findById(1L)).thenReturn(Optional.of(imovelExemplo()));
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.of(indicadorExemplo()));
        when(regraCalculoRepository.findByIndicadorAndVigente(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calcularSobreposicaoEmbargo(1L))
                .isInstanceOf(EstadoInconsistenteException.class);
    }
}