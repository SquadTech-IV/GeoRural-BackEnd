package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.mapper.IAEMapper;
import br.com.fatec.georural.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock IAEMapper mapper;

    @InjectMocks CalculoIAEService service;

    private ImovelRural imovelExemplo() {
        ImovelRural i = new ImovelRural();
        i.setId(1L);
        i.setAreaTotal(new BigDecimal("1000"));
        i.setVersao(new Versao());
        return i;
    }

    private Indicador indicadorExemplo() {
        Indicador ind = new Indicador();
        ind.setId(10L);
        ind.setSigla("IAE");
        return ind;
    }

    @Test
    void calcular_deveRetornarPercentualZero_quandoNaoHaEmbargo() {
        when(imovelRuralRepository.findById(1L)).thenReturn(Optional.of(imovelExemplo()));
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.of(indicadorExemplo()));
        when(regraCalculoRepository.findByIndicadorAndVigente(any(), any())).thenReturn(Optional.of(new RegraCalculo()));
        when(imovelRuralRepository.calcularAreaEmbargadaAgregada(1L))
                .thenReturn(new Object[]{null, new BigDecimal("1000")});
        when(resultadoIndicadorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(imovelRuralRepository.encontrarEmbargosIntersectantes(1L)).thenReturn(List.of());
        when(mapper.toResponse(any(), any())).thenAnswer(inv -> {
            ResultadoIndicador r = inv.getArgument(0);
            return new IAEResponse(1L, "IAE", r.getValorPercentual(), r.getValorHectares(),
                    r.getValorAbsoluto(), r.getDataCalculo(), List.of());
        });

        IAEResponse resposta = service.calcularSobreposicaoEmbargo(1L);

        assertThat(resposta.getValorPercentual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resposta.getEmbargosIntersectantes()).isEmpty();
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
