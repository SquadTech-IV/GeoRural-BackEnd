package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.ArquivoDownload;
import br.com.fatec.georural.dto.response.EnvioProcessamentoResponse;
import br.com.fatec.georural.entity.ArquivoDatalake;
import br.com.fatec.georural.entity.ArquivoDatalakeConteudo;
import br.com.fatec.georural.exception.ConflitoException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.gateway.ProcessamentoGateway;
import br.com.fatec.georural.mapper.ArquivoDatalakeMapper;
import br.com.fatec.georural.repository.ArquivoDatalakeConteudoRepository;
import br.com.fatec.georural.repository.ArquivoDatalakeItemRepository;
import br.com.fatec.georural.repository.ArquivoDatalakeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArquivoDatalakeServiceTest {

    @Mock ArquivoDatalakeRepository repository;
    @Mock ArquivoDatalakeItemRepository itemRepository;
    @Mock ArquivoDatalakeConteudoRepository conteudoRepository;
    @Mock ArquivoDatalakeMapper mapper;
    @Mock ProcessamentoGateway processamentoGateway;

    @InjectMocks ArquivoDatalakeService service;

    private ArquivoDatalake arquivoExemplo() {
        ArquivoDatalake a = new ArquivoDatalake();
        a.setId(1L);
        a.setNomeArquivo("CAR_-_Guarapuava_PR.zip");
        a.setFormato("ZIP");
        a.setSrid(4674);
        a.setCaminho("datalake/bruto/car/CAR_-_Guarapuava_PR.zip");
        return a;
    }

    @Test
    void baixar_deveRetornarConteudo_quandoExiste() {
        ArquivoDatalakeConteudo c = new ArquivoDatalakeConteudo();
        c.setArquivoId(1L);
        c.setContentType("application/zip");
        c.setConteudo(new byte[]{1, 2, 3});

        when(repository.findById(1L)).thenReturn(Optional.of(arquivoExemplo()));
        when(conteudoRepository.findById(1L)).thenReturn(Optional.of(c));

        ArquivoDownload dl = service.baixar(1L);

        assertThat(dl.nomeArquivo()).isEqualTo("CAR_-_Guarapuava_PR.zip");
        assertThat(dl.contentType()).isEqualTo("application/zip");
        assertThat(dl.conteudo()).containsExactly(1, 2, 3);
    }

    @Test
    void baixar_deveLancar404_quandoArquivoNaoExiste() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.baixar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }

    @Test
    void processar_deveEntregarAoGateway_quandoTemConteudo() {
        when(repository.findById(1L)).thenReturn(Optional.of(arquivoExemplo()));
        when(conteudoRepository.existsById(1L)).thenReturn(true);

        EnvioProcessamentoResponse resp = service.enviarParaProcessamento(1L);

        assertThat(resp.id()).isEqualTo(1L);
        assertThat(resp.downloadUrl()).isEqualTo("/api/arquivos/1/download");
        verify(processamentoGateway, times(1)).enviarParaProcessamento(any());
    }

    @Test
    void processar_deveLancar409_quandoNaoTemConteudo() {
        when(repository.findById(1L)).thenReturn(Optional.of(arquivoExemplo()));
        when(conteudoRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.enviarParaProcessamento(1L))
                .isInstanceOf(ConflitoException.class);

        verify(processamentoGateway, never()).enviarParaProcessamento(any());
    }
}