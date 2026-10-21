package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.qualidade.QualidadeResponse;
import br.com.fatec.georural.dto.response.versao.VersaoResponse;
import br.com.fatec.georural.entity.Auditoria;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.AuditoriaRepository;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.qualidade.QualidadeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicacaoServiceTest {

    @Mock VersaoRepository versaoRepository;
    @Mock ConjuntoDadosRepository conjuntoDadosRepository;
    @Mock AuditoriaRepository auditoriaRepository;
    @Mock
    QualidadeService qualidadeService;

    @InjectMocks PublicacaoService service;

    private Versao versaoRascunho(Long verId, Long conId, Long exeId) {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(conId);
        ExecucaoPipeline execucao = new ExecucaoPipeline();
        execucao.setId(exeId);
        Versao versao = new Versao();
        versao.setId(verId);
        versao.setNumero(1L);
        versao.setSituacao("RASCUNHO");
        versao.setConjunto(conjunto);
        versao.setExecucao(execucao);
        return versao;
    }

    private QualidadeResponse qualidade(Long exeId, boolean aprovado) {
        List<String> motivos = aprovado
                ? List.of()
                : List.of("Quarentena em 10,00% acima do limite de 2,00%.");
        return new QualidadeResponse(exeId, 1000, 980, 20, "APROVADO", 20, 98.0, aprovado, List.of(), motivos);
    }

    @Test
    void publicar_devePublicar_quandoQualidadeAprovada() {
        Versao versao = versaoRascunho(5L, 3L, 10L);
        when(versaoRepository.findById(5L)).thenReturn(Optional.of(versao));
        when(qualidadeService.avaliar(10L)).thenReturn(qualidade(10L, true));

        VersaoResponse resposta = service.publicar(5L);

        assertThat(versao.getSituacao()).isEqualTo("VIGENTE");
        assertThat(versao.getDataPublicacao()).isNotNull();
        assertThat(resposta.situacao()).isEqualTo("VIGENTE");
        assertThat(versao.getConjunto().getVersaoVigente()).isSameAs(versao);
        verify(versaoRepository).save(versao);
        verify(conjuntoDadosRepository).save(versao.getConjunto());
    }

    @Test
    void publicar_deveBloquear_quandoQualidadeReprovada() {
        Versao versao = versaoRascunho(5L, 3L, 10L);
        when(versaoRepository.findById(5L)).thenReturn(Optional.of(versao));
        when(qualidadeService.avaliar(10L)).thenReturn(qualidade(10L, false));

        assertThatThrownBy(() -> service.publicar(5L))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("qualidade reprovada");

        assertThat(versao.getSituacao()).isEqualTo("RASCUNHO");
        verify(versaoRepository, never()).save(any());
        verify(conjuntoDadosRepository, never()).save(any());
    }

    @Test
    void publicar_deveBloquear_quandoVersaoJaPublicada() {
        Versao versao = versaoRascunho(5L, 3L, 10L);
        versao.setSituacao("VIGENTE");
        when(versaoRepository.findById(5L)).thenReturn(Optional.of(versao));

        assertThatThrownBy(() -> service.publicar(5L))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("apenas RASCUNHO");
    }

    @Test
    void publicar_deveLancarNaoEncontrado_quandoVersaoInexistente() {
        when(versaoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publicar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void reprovar_deveRegistrarAuditoria_eManterRascunho() {
        Versao versao = versaoRascunho(5L, 3L, 10L);
        when(versaoRepository.findById(5L)).thenReturn(Optional.of(versao));

        VersaoResponse resposta = service.reprovar(5L, "Dados inconsistentes na area");

        assertThat(versao.getSituacao()).isEqualTo("RASCUNHO");
        assertThat(resposta.situacao()).isEqualTo("RASCUNHO");

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        Auditoria registro = captor.getValue();
        assertThat(registro.getEntidadeAfetada()).isEqualTo("VERSAO");
        assertThat(registro.getRegistroId()).isEqualTo(5L);
        assertThat(registro.getDadosNovos())
                .contains("REPROVACAO")
                .contains("Dados inconsistentes na area");
    }

    @Test
    void reprovar_deveBloquear_quandoVersaoJaPublicada() {
        Versao versao = versaoRascunho(5L, 3L, 10L);
        versao.setSituacao("ANTERIOR");
        when(versaoRepository.findById(5L)).thenReturn(Optional.of(versao));

        assertThatThrownBy(() -> service.reprovar(5L, "qualquer motivo"))
                .isInstanceOf(ConflitoException.class);

        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    void reprovar_deveBloquear_quandoMotivoVazio() {
        Versao versao = versaoRascunho(5L, 3L, 10L);
        when(versaoRepository.findById(5L)).thenReturn(Optional.of(versao));

        assertThatThrownBy(() -> service.reprovar(5L, "   "))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("motivo");

        verify(auditoriaRepository, never()).save(any());
    }
}