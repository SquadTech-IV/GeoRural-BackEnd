package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.qualidade.QualidadeResponse;
import br.com.fatec.georural.dto.response.qualidade.RegraAvaliadaResponse;
import br.com.fatec.georural.entity.RegraQualidade;
import br.com.fatec.georural.entity.Validacao;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.QuarentenaRepository;
import br.com.fatec.georural.repository.RegraQualidadeRepository;
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
    @Mock RegraQualidadeRepository regraQualidadeRepository;

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

    private RegraQualidade regra(String nome, String tipo, String severidade) {
        RegraQualidade r = new RegraQualidade();
        r.setNome(nome);
        r.setTipo(tipo);
        r.setSeveridade(severidade);
        r.setAtiva("S");
        return r;
    }

    @Test
    void avaliar_deveAprovar_quandoDentroDosCriterios() {
        when(execucaoRepository.existsById(1L)).thenReturn(true);
        when(validacaoRepository.findByExecucao_Id(1L))
                .thenReturn(List.of(validacao(1000, 980, 20, "APROVADO")));
        when(quarentenaRepository.contarPorExecucaoESituacao(1L, "PENDENTE")).thenReturn(20L);
        when(quarentenaRepository.agruparRegrasVioladas(1L, "PENDENTE")).thenReturn(List.<Object[]>of());
        when(regraQualidadeRepository.findByAtiva("S"))
                .thenReturn(List.of(regra("Geometria valida", "GEOMETRIA", "ERRO"),
                                    regra("Area dentro do municipio", "AREA", "AVISO")));

        QualidadeResponse r = service.avaliar(1L);

        assertThat(r.aprovado()).isTrue();
        assertThat(r.statusValidacao()).isEqualTo("APROVADO");
        assertThat(r.motivosReprovacao()).isEmpty();
        assertThat(r.regras()).hasSize(2);
        assertThat(r.regras()).allMatch(QualidadeServiceTest::passou);
    }

    @Test
    void avaliar_deveReprovar_quandoQuarentenaAcimaDoLimite() {
        when(execucaoRepository.existsById(1L)).thenReturn(true);
        when(validacaoRepository.findByExecucao_Id(1L))
                .thenReturn(List.of(validacao(1000, 950, 50, "APROVADO")));
        when(quarentenaRepository.contarPorExecucaoESituacao(1L, "PENDENTE")).thenReturn(50L);
        when(quarentenaRepository.agruparRegrasVioladas(1L, "PENDENTE")).thenReturn(List.<Object[]>of());
        when(regraQualidadeRepository.findByAtiva("S"))
                .thenReturn(List.of(regra("Geometria valida", "GEOMETRIA", "ERRO")));

        QualidadeResponse r = service.avaliar(1L);

        assertThat(r.aprovado()).isFalse();
        assertThat(r.motivosReprovacao()).anyMatch(m -> m.contains("Quarentena"));
        // a regra nao foi atribuida a nenhum registro -> aparece como aprovada no checklist
        assertThat(r.regras()).hasSize(1);
        assertThat(r.regras().get(0).aprovada()).isTrue();
    }

    @Test
    void avaliar_deveReprovar_quandoHaRegraDeSeveridadeErro() {
        when(execucaoRepository.existsById(1L)).thenReturn(true);
        when(validacaoRepository.findByExecucao_Id(1L))
                .thenReturn(List.of(validacao(1000, 990, 10, "APROVADO")));
        when(quarentenaRepository.contarPorExecucaoESituacao(1L, "PENDENTE")).thenReturn(10L);
        when(quarentenaRepository.agruparRegrasVioladas(1L, "PENDENTE"))
                .thenReturn(List.<Object[]>of(new Object[]{"Geometria valida", "GEOMETRIA", "ERRO", 10L}));
        when(regraQualidadeRepository.findByAtiva("S"))
                .thenReturn(List.of(regra("Geometria valida", "GEOMETRIA", "ERRO"),
                                    regra("Area dentro do municipio", "AREA", "AVISO")));

        QualidadeResponse r = service.avaliar(1L);

        assertThat(r.aprovado()).isFalse();
        assertThat(r.motivosReprovacao()).anyMatch(m -> m.contains("severidade bloqueante"));
        // checklist: 1 violada (ERRO, no topo) + 1 que passou
        assertThat(r.regras()).hasSize(2);
        RegraAvaliadaResponse violada = r.regras().get(0);
        assertThat(violada.nomeRegra()).isEqualTo("Geometria valida");
        assertThat(violada.aprovada()).isFalse();
        assertThat(violada.registrosAfetados()).isEqualTo(10L);
    }

    @Test
    void avaliar_deveLancarNaoEncontrado_quandoExecucaoInexistente() {
        when(execucaoRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.avaliar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private static boolean passou(RegraAvaliadaResponse r) {
        return r.aprovada() && r.registrosAfetados() == 0L;
    }
}
