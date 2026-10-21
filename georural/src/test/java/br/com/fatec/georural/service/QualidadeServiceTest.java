package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.qualidade.QualidadeResponse;
import br.com.fatec.georural.entity.Validacao;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.QuarentenaRepository;
import br.com.fatec.georural.repository.ValidacaoRepository;
import br.com.fatec.georural.service.qualidade.QualidadeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualidadeServiceTest {

    @Mock ExecucaoPipelineRepository execucaoRepository;
    @Mock ValidacaoRepository validacaoRepository;
    @Mock QuarentenaRepository quarentenaRepository;

    @InjectMocks
    QualidadeService service;

    private Validacao validacao(long total, long validos, long invalidos, String status) {
        Validacao v = new Validacao();
        v.setTotalRegistros(total);
        v.setRegistrosValidos(validos);
        v.setRegistrosInvalidos(invalidos);
        v.setStatusFinal(status);
        return v;
    }

    @Test
    void avaliar_deveAprovar_quandoDentroDosCriterios() {
        when(execucaoRepository.existsById(1L)).thenReturn(true);
        when(validacaoRepository.findByExecucao_Id(1L))
                .thenReturn(List.of(validacao(1000, 980, 20, "APROVADO")));
        when(quarentenaRepository.contarPorExecucaoESituacao(1L, "PENDENTE")).thenReturn(20L);
        when(quarentenaRepository.agruparRegrasVioladas(1L, "PENDENTE")).thenReturn(List.<Object[]>of());

        QualidadeResponse r = service.avaliar(1L);

        assertThat(r.aprovado()).isTrue();
        assertThat(r.statusValidacao()).isEqualTo("APROVADO");
        assertThat(r.motivosReprovacao()).isEmpty();
    }

    @Test
    void avaliar_deveReprovar_quandoQuarentenaAcimaDoLimite() {
        when(execucaoRepository.existsById(1L)).thenReturn(true);
        when(validacaoRepository.findByExecucao_Id(1L))
                .thenReturn(List.of(validacao(1000, 950, 50, "APROVADO")));
        when(quarentenaRepository.contarPorExecucaoESituacao(1L, "PENDENTE")).thenReturn(50L);
        when(quarentenaRepository.agruparRegrasVioladas(1L, "PENDENTE")).thenReturn(List.<Object[]>of());

        QualidadeResponse r = service.avaliar(1L);

        assertThat(r.aprovado()).isFalse();
        assertThat(r.motivosReprovacao()).anyMatch(m -> m.contains("Quarentena"));
    }

    @Test
    void avaliar_deveReprovar_quandoHaRegraDeSeveridadeErro() {
        when(execucaoRepository.existsById(1L)).thenReturn(true);
        when(validacaoRepository.findByExecucao_Id(1L))
                .thenReturn(List.of(validacao(1000, 990, 10, "APROVADO")));
        when(quarentenaRepository.contarPorExecucaoESituacao(1L, "PENDENTE")).thenReturn(10L);
        when(quarentenaRepository.agruparRegrasVioladas(1L, "PENDENTE"))
                .thenReturn(List.<Object[]>of(new Object[]{"Geometria valida", "GEOMETRIA", "ERRO", 10L}));

        QualidadeResponse r = service.avaliar(1L);

        assertThat(r.aprovado()).isFalse();
        assertThat(r.regrasVioladas()).hasSize(1);
        assertThat(r.motivosReprovacao()).anyMatch(m -> m.contains("severidade bloqueante"));
    }

    @Test
    void avaliar_deveLancarNaoEncontrado_quandoExecucaoInexistente() {
        when(execucaoRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.avaliar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}