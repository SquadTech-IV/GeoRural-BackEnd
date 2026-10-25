package br.com.fatec.georural.service;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.fatec.georural.entity.Embargo;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.repository.EmbargoRepository;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class VersaoEmbargoServiceTest {

    @Mock EmbargoRepository embargoRepository;
    @InjectMocks VersaoEmbargoService service;

    @Test
    void resolverId_deveRetornarNumeroDaVersaoMaisRecente() {
        Versao versao = new Versao();
        versao.setNumero(61L);
        Embargo embargo = new Embargo();
        embargo.setVersao(versao);
        when(embargoRepository.findFirstByOrderByVersaoDesc()).thenReturn(Optional.of(embargo));

        assertThat(service.resolverId()).isEqualTo(61L);
    }

    @Test
    void resolverId_deveLancarEstadoInconsistente_quandoNaoHaVersao() {
        when(embargoRepository.findFirstByOrderByVersaoDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolverId())
                .isInstanceOf(EstadoInconsistenteException.class);
    }
}
