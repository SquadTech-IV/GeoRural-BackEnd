package br.com.fatec.georural.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.repository.VersaoRepository;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class VersaoEmbargoServiceTest {

    @Mock VersaoRepository versaoRepository;
    @InjectMocks VersaoEmbargoService service;

    @Test
    void resolverId_deveRetornarVersaoMaisRecente() {
        when(versaoRepository.encontrarVersaoEmbargoMaisRecente()).thenReturn(Optional.of(61L));

        assertThat(service.resolverId()).isEqualTo(61L);
    }

    @Test
    void resolverId_deveLancarEstadoInconsistente_quandoNaoHaVersao() {
        when(versaoRepository.encontrarVersaoEmbargoMaisRecente()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolverId())
                .isInstanceOf(EstadoInconsistenteException.class);
    }
}
