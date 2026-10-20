package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.trajetoria.ConjuntoResumoResponse;
import br.com.fatec.georural.dto.response.trajetoria.ConjuntoTrajetoriaResponse;
import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TrajetoriaConjuntoServiceTest {

    @Mock private ConjuntoDadosRepository conjuntoRepository;
    @Mock private ArquivoBrutoRepository arquivoBrutoRepository;
    @Mock private MovimentacaoZonaRepository movimentacaoZonaRepository;
    @Mock private ValidacaoRepository validacaoRepository;

    @InjectMocks
    private TrajetoriaConjuntoService service;

    private ConjuntoDados conjuntoComFonte(String nomeFonte, String nomeConjunto) {
        FonteDoDado fonte = new FonteDoDado();
        fonte.setNome(nomeFonte);

        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(1L);
        conjunto.setNome(nomeConjunto);
        conjunto.setFonte(fonte);
        return conjunto;
    }

    private ArquivoBruto arquivo(ConjuntoDados conjunto, String nome) {
        ArquivoBruto arquivo = new ArquivoBruto();
        arquivo.setId(10L);
        arquivo.setNome(nome);
        arquivo.setConjunto(conjunto);
        arquivo.setDataUpload(LocalDateTime.of(2026, 10, 18, 3, 0));
        return arquivo;
    }

    private MovimentacaoZona movimentacao(ArquivoBruto arquivo, String zona, LocalDateTime data, String obs) {
        MovimentacaoZona mov = new MovimentacaoZona();
        mov.setArquivoBruto(arquivo);
        mov.setZona(zona);
        mov.setData(data);
        mov.setObservacao(obs);
        return mov;
    }

    @Test
    void listarConjuntosDeveMontarRotuloComFonteENome() {
        ConjuntoDados conjunto = conjuntoComFonte("INPE", "Focos de calor");
        when(conjuntoRepository.findAll()).thenReturn(List.of(conjunto));

        List<ConjuntoResumoResponse> resultado = service.listarConjuntos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).rotulo()).isEqualTo("INPE · Focos de calor");
    }

    @Test
    void listarConjuntosDeveUsarSoONomeQuandoNaoTiverFonte() {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(2L);
        conjunto.setNome("Sem fonte vinculada");
        when(conjuntoRepository.findAll()).thenReturn(List.of(conjunto));

        List<ConjuntoResumoResponse> resultado = service.listarConjuntos();

        assertThat(resultado.get(0).rotulo()).isEqualTo("Sem fonte vinculada");
    }

    @Test
    void deveLancarExcecaoQuandoConjuntoNaoExistir() {
        when(conjuntoRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarTrajetoriaPorArquivo(999L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("999");
    }

    @Test
    void arquivoSemMovimentacaoDeveTerTodasAsZonasPendentes() {
        ConjuntoDados conjunto = conjuntoComFonte("INPE", "Focos de calor");
        ArquivoBruto arq = arquivo(conjunto, "focos_2026-10-16.geojson");

        when(conjuntoRepository.findById(1L)).thenReturn(Optional.of(conjunto));
        when(arquivoBrutoRepository.findByConjunto_IdOrderByDataUploadAsc(1L)).thenReturn(List.of(arq));
        when(movimentacaoZonaRepository.findByArquivoBrutoIdOrderByDataAsc(10L)).thenReturn(List.of());
        when(validacaoRepository.findFirstByArquivoBruto_IdOrderByDataExecucaoDesc(10L)).thenReturn(Optional.empty());

        ConjuntoTrajetoriaResponse resultado = service.consultarTrajetoriaPorArquivo(1L);

        var arquivoResp = resultado.arquivos().get(0);
        assertThat(arquivoResp.zonaAtual()).isEqualTo("BRUTA");
        assertThat(arquivoResp.passagens()).allMatch(p -> p.status().equals("pendente"));
    }

    @Test
    void arquivoEmTratamentoDeveMarcarTratadaComoAtualEPublicadaComoPendente() {
        ConjuntoDados conjunto = conjuntoComFonte("INPE", "Focos de calor");
        ArquivoBruto arq = arquivo(conjunto, "focos_2026-10-18.geojson");

        List<MovimentacaoZona> movs = List.of(
                movimentacao(arq, "BRUTA", LocalDateTime.of(2026, 10, 18, 3, 0), "Recebido por API"),
                movimentacao(arq, "QUARENTENA", LocalDateTime.of(2026, 10, 18, 3, 6), "Validação sem pendências"),
                movimentacao(arq, "TRATADA", LocalDateTime.of(2026, 10, 18, 3, 12), "Padronizando")
        );

        Validacao validacao = new Validacao();
        validacao.setTotalRegistros(1284L);
        validacao.setRegistrosValidos(1284L);
        validacao.setRegistrosInvalidos(0L);

        when(conjuntoRepository.findById(1L)).thenReturn(Optional.of(conjunto));
        when(arquivoBrutoRepository.findByConjunto_IdOrderByDataUploadAsc(1L)).thenReturn(List.of(arq));
        when(movimentacaoZonaRepository.findByArquivoBrutoIdOrderByDataAsc(10L)).thenReturn(movs);
        when(validacaoRepository.findFirstByArquivoBruto_IdOrderByDataExecucaoDesc(10L)).thenReturn(Optional.of(validacao));

        ConjuntoTrajetoriaResponse resultado = service.consultarTrajetoriaPorArquivo(1L);
        var passagens = resultado.arquivos().get(0).passagens();

        assertThat(resultado.arquivos().get(0).zonaAtual()).isEqualTo("TRATADA");
        assertThat(passagens.get(0).status()).isEqualTo("done");       // BRUTA
        assertThat(passagens.get(1).status()).isEqualTo("done");       // QUARENTENA (sem invalidos)
        assertThat(passagens.get(2).status()).isEqualTo("atual");      // TRATADA (mais avançada)
        assertThat(passagens.get(3).status()).isEqualTo("pendente");   // PUBLICADA
    }

    @Test
    void quarentenaComRegistrosRetidosDeveSerRessalva() {
        ConjuntoDados conjunto = conjuntoComFonte("INPE", "Focos de calor");
        ArquivoBruto arq = arquivo(conjunto, "focos_2026-10-17.geojson");

        List<MovimentacaoZona> movs = List.of(
                movimentacao(arq, "BRUTA", LocalDateTime.of(2026, 10, 17, 3, 0), null),
                movimentacao(arq, "QUARENTENA", LocalDateTime.of(2026, 10, 17, 3, 5), "Coordenada fora do PR"),
                movimentacao(arq, "TRATADA", LocalDateTime.of(2026, 10, 17, 3, 11), null)
        );

        Validacao validacao = new Validacao();
        validacao.setTotalRegistros(1197L);
        validacao.setRegistrosValidos(1185L);
        validacao.setRegistrosInvalidos(12L);

        when(conjuntoRepository.findById(1L)).thenReturn(Optional.of(conjunto));
        when(arquivoBrutoRepository.findByConjunto_IdOrderByDataUploadAsc(1L)).thenReturn(List.of(arq));
        when(movimentacaoZonaRepository.findByArquivoBrutoIdOrderByDataAsc(10L)).thenReturn(movs);
        when(validacaoRepository.findFirstByArquivoBruto_IdOrderByDataExecucaoDesc(10L)).thenReturn(Optional.of(validacao));

        ConjuntoTrajetoriaResponse resultado = service.consultarTrajetoriaPorArquivo(1L);
        var passagens = resultado.arquivos().get(0).passagens();

        assertThat(passagens.get(1).status()).isEqualTo("ressalva");
        assertThat(passagens.get(1).contagem()).isEqualTo("12 retidos · 1185 seguiram");
    }

    @Test
    void arquivoPublicadoDeveTerTodasAsZonasDoneETerPublicadaComoAtual() {
        ConjuntoDados conjunto = conjuntoComFonte("CAR", "Guarapuava");
        ArquivoBruto arq = arquivo(conjunto, "CAR_Guarapuava_PR.zip");

        List<MovimentacaoZona> movs = List.of(
                movimentacao(arq, "BRUTA", LocalDateTime.of(2026, 9, 28, 9, 40), null),
                movimentacao(arq, "QUARENTENA", LocalDateTime.of(2026, 9, 28, 9, 44), null),
                movimentacao(arq, "TRATADA", LocalDateTime.of(2026, 9, 28, 9, 52), null),
                movimentacao(arq, "PUBLICADA", LocalDateTime.of(2026, 9, 28, 10, 3), "Publicado (imutável)")
        );

        when(conjuntoRepository.findById(1L)).thenReturn(Optional.of(conjunto));
        when(arquivoBrutoRepository.findByConjunto_IdOrderByDataUploadAsc(1L)).thenReturn(List.of(arq));
        when(movimentacaoZonaRepository.findByArquivoBrutoIdOrderByDataAsc(10L)).thenReturn(movs);
        when(validacaoRepository.findFirstByArquivoBruto_IdOrderByDataExecucaoDesc(10L)).thenReturn(Optional.empty());

        ConjuntoTrajetoriaResponse resultado = service.consultarTrajetoriaPorArquivo(1L);
        var arquivoResp = resultado.arquivos().get(0);

        assertThat(arquivoResp.zonaAtual()).isEqualTo("PUBLICADA");
        assertThat(arquivoResp.passagens()).allMatch(p -> p.status().equals("done"));
    }
}
