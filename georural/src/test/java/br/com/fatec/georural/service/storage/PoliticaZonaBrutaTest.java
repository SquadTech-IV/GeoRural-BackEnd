package br.com.fatec.georural.service.storage;

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.HeadObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.HeadObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PoliticaZonaBrutaTest {

    private ObjectStorage cliente;

    @BeforeEach
    void setUp() {
        cliente = Mockito.mock(ObjectStorage.class);
    }

    private static BmcException bmc(int status) {
        return new BmcException(status, "CodigoDeTeste", "falha simulada", "opc-teste");
    }

    private static InputStream conteudo() {
        return new ByteArrayInputStream(new byte[] {1, 2, 3});
    }

    // ---------------------------------------------------------------- gravação

    @Test
    void enviarObjeto_deveEnviarComIfNoneMatchQualquerParaNuncaSobrescrever() {
        Map<String, String> metadados = Map.of("hash-sha256", "abc", "con-id", "1");

        PoliticaZonaBruta.enviarObjeto(cliente, "ns-teste", "bucket-teste", "conjuntos/1/a.csv", conteudo(), 3L, metadados);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cliente).putObject(captor.capture());
        PutObjectRequest requisicao = captor.getValue();
        assertEquals("*", requisicao.getIfNoneMatch());
        assertEquals("ns-teste", requisicao.getNamespaceName());
        assertEquals("bucket-teste", requisicao.getBucketName());
        assertEquals("conjuntos/1/a.csv", requisicao.getObjectName());
        assertEquals(3L, requisicao.getContentLength().longValue());
        assertEquals(metadados, requisicao.getOpcMeta());
    }

    @Test
    void enviarObjeto_semMetadados_deveEnviarSemMapaDeMetadados() {
        PoliticaZonaBruta.enviarObjeto(cliente, "ns", "bucket", "a.csv", conteudo(), 3L, null);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cliente).putObject(captor.capture());
        Map<String, String> enviados = captor.getValue().getOpcMeta();
        assertTrue(enviados == null || enviados.isEmpty());
        assertEquals("*", captor.getValue().getIfNoneMatch());
    }

    @Test
    void enviarObjeto_objetoJaExistente_devePropagarOConflito412() {
        when(cliente.putObject(any(PutObjectRequest.class))).thenThrow(bmc(412));

        BmcException erro = assertThrows(BmcException.class,
                () -> PoliticaZonaBruta.enviarObjeto(cliente, "ns", "bucket", "a.csv", conteudo(), 3L, null));

        assertTrue(PoliticaZonaBruta.ehConflitoPorObjetoJaExistente(erro));
    }

    @Test
    void enviarObjeto_argumentosInvalidos_deveLancarIllegalArgumentSemChamarOCliente() {
        assertThrows(IllegalArgumentException.class,
                () -> PoliticaZonaBruta.enviarObjeto(null, "ns", "b", "c", conteudo(), 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> PoliticaZonaBruta.enviarObjeto(cliente, " ", "b", "c", conteudo(), 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> PoliticaZonaBruta.enviarObjeto(cliente, "ns", null, "c", conteudo(), 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> PoliticaZonaBruta.enviarObjeto(cliente, "ns", "b", "", conteudo(), 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> PoliticaZonaBruta.enviarObjeto(cliente, "ns", "b", "c", null, 1L, null));

        verifyNoInteractions(cliente);
    }

    // ---------------------------------------------------------------- existência

    @Test
    void objetoExiste_quandoOHeadResponde_deveRetornarVerdadeiro() {
        HeadObjectResponse resposta = Mockito.mock(HeadObjectResponse.class);
        when(cliente.headObject(any(HeadObjectRequest.class))).thenReturn(resposta);

        assertTrue(PoliticaZonaBruta.objetoExiste(cliente, "ns", "bucket", "a.csv"));
    }

    @Test
    void objetoExiste_quandoOHeadResponde404_deveRetornarFalso() {
        when(cliente.headObject(any(HeadObjectRequest.class))).thenThrow(bmc(404));

        assertFalse(PoliticaZonaBruta.objetoExiste(cliente, "ns", "bucket", "a.csv"));
    }

    @Test
    void objetoExiste_outrosErros_devemSerPropagadosENaoViramFalso() {
        when(cliente.headObject(any(HeadObjectRequest.class))).thenThrow(bmc(403));

        BmcException erro = assertThrows(BmcException.class,
                () -> PoliticaZonaBruta.objetoExiste(cliente, "ns", "bucket", "a.csv"));

        assertEquals(403, erro.getStatusCode());
    }

    @Test
    void objetoExiste_deveConsultarOCaminhoInformado() {
        when(cliente.headObject(any(HeadObjectRequest.class))).thenThrow(bmc(404));

        PoliticaZonaBruta.objetoExiste(cliente, "ns-teste", "bucket-teste", "conjuntos/1/a.csv");

        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(cliente).headObject(captor.capture());
        assertEquals("ns-teste", captor.getValue().getNamespaceName());
        assertEquals("bucket-teste", captor.getValue().getBucketName());
        assertEquals("conjuntos/1/a.csv", captor.getValue().getObjectName());
    }

    // ------------------------------------------------------------------- conflito

    @Test
    void ehConflitoPorObjetoJaExistente_apenasOStatus412() {
        assertTrue(PoliticaZonaBruta.ehConflitoPorObjetoJaExistente(bmc(412)));
        assertFalse(PoliticaZonaBruta.ehConflitoPorObjetoJaExistente(bmc(404)));
        assertFalse(PoliticaZonaBruta.ehConflitoPorObjetoJaExistente(bmc(409)));
        assertFalse(PoliticaZonaBruta.ehConflitoPorObjetoJaExistente(bmc(500)));
        assertFalse(PoliticaZonaBruta.ehConflitoPorObjetoJaExistente(null));
    }

    // ----------------------------------------------------------- somente criação

    @Test
    void classe_naoDeveExporMetodosDeApagarOuAlterar() {
        for (Method metodo : PoliticaZonaBruta.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(metodo.getModifiers())) {
                continue;
            }
            String nome = metodo.getName().toLowerCase(Locale.ROOT);
            for (String proibido : new String[] {"delete", "apagar", "remover", "excluir", "atualizar", "alterar", "sobrescrever", "update"}) {
                assertFalse(nome.contains(proibido), "Método proibido na zona bruta: " + metodo.getName());
            }
        }
    }
}