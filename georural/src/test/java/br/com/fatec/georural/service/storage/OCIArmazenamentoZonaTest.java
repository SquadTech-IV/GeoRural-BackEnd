package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.config.DataLakeProperties;
import br.com.fatec.georural.exception.ObjetoJaExisteException;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.HeadObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OCIArmazenamentoZonaTest {

    private ObjectStorage cliente;
    private OCIArmazenamentoZona armazenamento;

    @BeforeEach
    void setUp() {
        cliente = Mockito.mock(ObjectStorage.class);
        DataLakeProperties propriedades = new DataLakeProperties(
                "sa-saopaulo-1",
                "ns-teste",
                new DataLakeProperties.Bucket("bucket-bruta", "bucket-quarentena", "bucket-tratada", "bucket-publicada"),
                null,
                null);
        armazenamento = new OCIArmazenamentoZona(cliente, propriedades);
    }

    @Test
    void gravar_deveGravarNoBucketDaZonaBrutaSemSobrescrever() {
        Map<String, String> metadados = Map.of("hash-sha256", "abc");

        armazenamento.gravar(Zona.BRUTA, "conjuntos/1/a.csv", new ByteArrayInputStream(new byte[] {1, 2, 3}), 3L, metadados);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cliente).putObject(captor.capture());
        PutObjectRequest requisicao = captor.getValue();
        assertEquals("ns-teste", requisicao.getNamespaceName());
        assertEquals("bucket-bruta", requisicao.getBucketName());
        assertEquals("conjuntos/1/a.csv", requisicao.getObjectName());
        assertEquals("*", requisicao.getIfNoneMatch());
        assertEquals(metadados, requisicao.getOpcMeta());
    }

    @Test
    void gravar_objetoJaExistente_deveLancarObjetoJaExiste() {
        when(cliente.putObject(any(PutObjectRequest.class)))
                .thenThrow(new BmcException(412, "NoSuchBucket", "objeto já existe", "opc-teste"));

        ObjetoJaExisteException erro = assertThrows(ObjetoJaExisteException.class, () -> armazenamento.gravar(Zona.BRUTA,
                "a.csv", new ByteArrayInputStream(new byte[] {1}), 1L, null));

        assertEquals("a.csv", erro.getCaminho());
        assertTrue(erro.getCause() instanceof BmcException);
    }

    @Test
    void existe_deveConsultarOBucketDaZonaBruta() {
        when(cliente.headObject(any(HeadObjectRequest.class)))
                .thenThrow(new BmcException(404, "NotFound", "não existe", "opc-teste"));

        boolean existe = armazenamento.existe(Zona.BRUTA, "conjuntos/1/a.csv");

        assertFalse(existe);
        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(cliente).headObject(captor.capture());
        assertEquals("ns-teste", captor.getValue().getNamespaceName());
        assertEquals("bucket-bruta", captor.getValue().getBucketName());
        assertEquals("conjuntos/1/a.csv", captor.getValue().getObjectName());
    }

    @Test
    void ler_deveDevolverOConteudoDoObjetoDoBucketDaZonaBruta() throws IOException {
        byte[] conteudo = "abc".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse resposta = Mockito.mock(GetObjectResponse.class);
        when(resposta.getInputStream()).thenReturn(new ByteArrayInputStream(conteudo));
        when(cliente.getObject(any(GetObjectRequest.class))).thenReturn(resposta);

        try (InputStream lido = armazenamento.ler(Zona.BRUTA, "conjuntos/1/a.csv")) {
            assertArrayEquals(conteudo, lido.readAllBytes());
        }

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(cliente).getObject(captor.capture());
        assertEquals("ns-teste", captor.getValue().getNamespaceName());
        assertEquals("bucket-bruta", captor.getValue().getBucketName());
        assertEquals("conjuntos/1/a.csv", captor.getValue().getObjectName());
    }

    @Test
    void ler_caminhoEmBranco_deveLancarIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> armazenamento.ler(Zona.BRUTA, " "));
        assertThrows(IllegalArgumentException.class, () -> armazenamento.ler(Zona.BRUTA, null));
    }

    @Test
    void operacoes_nuncaDevemApagarObjetosNaZonaBruta() {
        when(cliente.headObject(any(HeadObjectRequest.class)))
                .thenThrow(new BmcException(404, "NotFound", "não existe", "opc-teste"));

        armazenamento.gravar(Zona.BRUTA, "a.csv", new ByteArrayInputStream(new byte[] {1}), 1L, null);
        armazenamento.existe(Zona.BRUTA, "a.csv");

        verify(cliente, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void classeEInterface_naoDevemExporMetodosDeApagarOuAlterar() {
        for (Class<?> tipo : new Class<?>[] {OCIArmazenamentoZona.class, ArmazenamentoZonaPort.class}) {
            for (Method metodo : tipo.getDeclaredMethods()) {
                if (!Modifier.isPublic(metodo.getModifiers())) {
                    continue;
                }
                String nome = metodo.getName().toLowerCase(Locale.ROOT);
                for (String proibido : new String[] {"delete", "apagar", "remover", "excluir", "atualizar", "alterar", "sobrescrever", "update"}) {
                    assertFalse(nome.contains(proibido), "Método proibido na zona bruta: " + tipo.getSimpleName() + "." + metodo.getName());
                }
            }
        }
    }
}