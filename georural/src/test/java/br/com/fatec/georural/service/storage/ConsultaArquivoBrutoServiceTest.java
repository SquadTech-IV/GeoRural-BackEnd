package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.dto.response.ArquivoBrutoResumoResponse;
import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ArquivoBrutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConsultaArquivoBrutoServiceTest {

    private ArquivoBrutoRepository repositorio;
    private ConsultaArquivoBrutoService service;

    @BeforeEach
    void setUp() {
        repositorio = Mockito.mock(ArquivoBrutoRepository.class);
        service = new ConsultaArquivoBrutoService(repositorio);

        when(repositorio.findAllBy(any(Pageable.class))).thenReturn(Page.empty());
        when(repositorio.findByConjuntoId(any(), any(Pageable.class))).thenReturn(Page.empty());
        when(repositorio.findByStatus(any(), any(Pageable.class))).thenReturn(Page.empty());
        when(repositorio.findByConjuntoIdAndStatus(any(), any(), any(Pageable.class))).thenReturn(Page.empty());
    }

    private static ArquivoBruto registro(Usuario usuario) {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(1L);
        conjunto.setNome("CAR - Guarapuava");

        ArquivoBruto registro = new ArquivoBruto();
        registro.setId(10L);
        registro.setConjunto(conjunto);
        registro.setUsuario(usuario);
        registro.setNome("dados.csv");
        registro.setFormato("CSV");
        registro.setTamanhoBytes(3L);
        registro.setHash("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        registro.setStatus("RECEBIDO");
        registro.setPathArmazenamento("conjuntos/1/brutos/2026/10/x_dados.csv");
        registro.setDataUpload(LocalDateTime.of(2026, 10, 22, 16, 0));
        return registro;
    }

    // ------------------------------------------------------------- escolha da consulta

    @Test
    void listar_semFiltros_deveUsarPaginacaoOrdenadaDoMaisRecenteParaOMaisAntigo() {
        service.listar(null, null, 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).findAllBy(captor.capture());
        Pageable paginacao = captor.getValue();
        assertEquals(0, paginacao.getPageNumber());
        assertEquals(20, paginacao.getPageSize());
        assertEquals(Sort.Direction.DESC, paginacao.getSort().getOrderFor("dataUpload").getDirection());
        assertEquals(Sort.Direction.DESC, paginacao.getSort().getOrderFor("id").getDirection());
    }

    @Test
    void listar_comConjunto_deveFiltrarPorConjunto() {
        service.listar(1L, null, 2, 50);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio).findByConjuntoId(eq(1L), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
        verify(repositorio, never()).findAllBy(any(Pageable.class));
    }

    @Test
    void listar_comSituacao_deveNormalizarEFiltrarPorSituacao() {
        service.listar(null, "  recebido ", 0, 20);

        verify(repositorio).findByStatus(eq("RECEBIDO"), any(Pageable.class));
    }

    @Test
    void listar_comConjuntoESituacao_deveFiltrarPelosDois() {
        service.listar(1L, "FALHA", 0, 20);

        verify(repositorio).findByConjuntoIdAndStatus(eq(1L), eq("FALHA"), any(Pageable.class));
    }

    @Test
    void listar_situacaoEmBranco_deveSerTratadaComoSemFiltro() {
        service.listar(null, "   ", 0, 20);

        verify(repositorio).findAllBy(any(Pageable.class));
        verify(repositorio, never()).findByStatus(any(), any(Pageable.class));
    }

    // ----------------------------------------------------------------- validações

    @Test
    void listar_situacaoDesconhecida_deveLancarRequisicaoInvalidaSemConsultar() {
        assertThrows(RequisicaoInvalidaException.class, () -> service.listar(null, "QUALQUER", 0, 20));

        verifyNoInteractions(repositorio);
    }

    @Test
    void listar_paginaNegativa_deveLancarRequisicaoInvalida() {
        assertThrows(RequisicaoInvalidaException.class, () -> service.listar(null, null, -1, 20));

        verifyNoInteractions(repositorio);
    }

    @Test
    void listar_tamanhoForaDosLimites_deveLancarRequisicaoInvalida() {
        assertThrows(RequisicaoInvalidaException.class, () -> service.listar(null, null, 0, 0));
        assertThrows(RequisicaoInvalidaException.class, () -> service.listar(null, null, 0, 101));

        verifyNoInteractions(repositorio);
    }

    @Test
    void listar_tamanhoNosLimites_deveAceitar() {
        assertDoesNotThrow(() -> service.listar(null, null, 0, 1));
        assertDoesNotThrow(() -> service.listar(null, null, 0, 100));
    }

    // -------------------------------------------------------------------- resposta

    @Test
    void listar_deveMontarOItemComConjuntoEResponsavelEOsDadosDaPagina() {
        Usuario usuario = new Usuario();
        usuario.setId(2L);
        usuario.setNome("Usuário OPERADOR");
        Page<ArquivoBruto> pagina = new PageImpl<>(List.of(registro(usuario)), PageRequest.of(1, 5), 11);
        when(repositorio.findAllBy(any(Pageable.class))).thenReturn(pagina);

        PaginaResponse<ArquivoBrutoResumoResponse> resposta = service.listar(null, null, 1, 5);

        assertEquals(1, resposta.pagina());
        assertEquals(5, resposta.tamanho());
        assertEquals(11L, resposta.totalItens());
        assertEquals(3, resposta.totalPaginas());
        ArquivoBrutoResumoResponse item = resposta.itens().get(0);
        assertEquals(10L, item.id());
        assertEquals(1L, item.conjuntoId());
        assertEquals("CAR - Guarapuava", item.conjuntoNome());
        assertEquals("dados.csv", item.nome());
        assertEquals("CSV", item.formato());
        assertEquals(3L, item.tamanhoBytes());
        assertNotNull(item.hash());
        assertEquals("SHA-256", item.algoritmoHash());
        assertEquals("UPLOAD", item.canalEntrada());
        assertEquals("RECEBIDO", item.status());
        assertEquals(2L, item.responsavelId());
        assertEquals("Usuário OPERADOR", item.responsavelNome());
        assertEquals(LocalDateTime.of(2026, 10, 22, 16, 0), item.dataUpload());
    }

    @Test
    void listar_registroSemUsuario_deveDevolverResponsavelNulo() {
        Page<ArquivoBruto> pagina = new PageImpl<>(List.of(registro(null)), PageRequest.of(0, 20), 1);
        when(repositorio.findAllBy(any(Pageable.class))).thenReturn(pagina);

        ArquivoBrutoResumoResponse item = service.listar(null, null, 0, 20).itens().get(0);

        assertNull(item.responsavelId());
        assertNull(item.responsavelNome());
    }
}