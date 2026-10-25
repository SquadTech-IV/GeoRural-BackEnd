package br.com.fatec.georural.service.storage;

import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidadorFormatoArquivoTest {

    private final ValidadorFormatoArquivo validador = new ValidadorFormatoArquivo();

    @TempDir
    Path pasta;

    // ---------------------------------------------------------------- extensão

    @ParameterizedTest
    @CsvSource({
            "dados.zip,SHP",
            "DADOS.ZIP,SHP",
            "mapa.geojson,GEOJSON",
            "mapa.GeoJSON,GEOJSON",
            "base.gpkg,GPKG",
            "tabela.csv,CSV",
            "imagem.tif,TIF",
            "imagem.tiff,TIF",
            "IMAGEM.TIFF,TIF"
    })
    void validarExtensao_deveRetornarOFormatoGravadoNoRegistro(String nome, String esperado) {
        assertEquals(esperado, validador.validarExtensao(nome));
    }

    @ParameterizedTest
    @ValueSource(strings = {"dados.json", "DADOS.JSON", "dados.geojson.json", "dados.txt", "dados.shp", "dados.zip.exe", "dados"})
    void validarExtensao_deveRejeitarExtensoesNaoAceitas(String nome) {
        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarExtensao(nome));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void validarExtensao_deveRejeitarNomeAusente(String nome) {
        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarExtensao(nome));
    }

    // ----------------------------------------------------------- shapefile .zip

    @Test
    void validarConteudo_shapefileCompleto_deveAceitar() throws IOException {
        Path arquivo = zip("a.shp", "a.shx", "a.dbf");

        assertDoesNotThrow(() -> validador.validarConteudo(arquivo, "SHP"));
    }

    @Test
    void validarConteudo_shapefileComArquivosExtrasENomesEmMaiusculo_deveAceitar() throws IOException {
        Path arquivo = zip("DADOS/A.SHP", "DADOS/A.SHX", "DADOS/A.DBF", "DADOS/A.PRJ", "__MACOSX/DADOS/._A.shp");

        assertDoesNotThrow(() -> validador.validarConteudo(arquivo, "SHP"));
    }

    @Test
    void validarConteudo_shapefileSemShx_deveRejeitar() throws IOException {
        Path arquivo = zip("a.shp", "a.dbf");

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "SHP"));
    }

    @Test
    void validarConteudo_shapefileSemDbf_deveRejeitar() throws IOException {
        Path arquivo = zip("a.shp", "a.shx");

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "SHP"));
    }

    @Test
    void validarConteudo_shapefileComNomesBaseDiferentes_deveRejeitar() throws IOException {
        Path arquivo = zip("a.shp", "b.shx", "c.dbf");

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "SHP"));
    }

    @Test
    void validarConteudo_shapefileSoComEntradasMacosx_deveRejeitar() throws IOException {
        Path arquivo = zip("__MACOSX/a.shp", "__MACOSX/a.shx", "__MACOSX/a.dbf", "leia-me.txt");

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "SHP"));
    }

    @Test
    void validarConteudo_zipSemAssinatura_deveRejeitar() throws IOException {
        Path arquivo = arquivo("isto nao e um zip".getBytes(StandardCharsets.UTF_8));

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "SHP"));
    }

    @Test
    void validarConteudo_zipTruncadoNaAssinatura_deveRejeitar() throws IOException {
        Path arquivo = arquivo(new byte[] {0x50, 0x4B});

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "SHP"));
    }

    @Test
    void validarConteudo_zipCorrompidoComAssinaturaValida_deveRejeitar() throws IOException {
        Path arquivo = arquivo(new byte[] {0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10});

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "SHP"));
    }

    // ------------------------------------------------------------------ GeoPackage

    @Test
    void validarConteudo_geopackageComCabecalhoSqlite_deveAceitar() throws IOException {
        byte[] conteudo = new byte[100];
        byte[] assinatura = "SQLite format 3\u0000".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(assinatura, 0, conteudo, 0, assinatura.length);
        Path arquivo = arquivo(conteudo);

        assertDoesNotThrow(() -> validador.validarConteudo(arquivo, "GPKG"));
    }

    @Test
    void validarConteudo_geopackageSemCabecalhoSqlite_deveRejeitar() throws IOException {
        Path arquivo = arquivo("SQLite format 2\u0000 ....".getBytes(StandardCharsets.US_ASCII));

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "GPKG"));
    }

    // ---------------------------------------------------------------------- TIFF

    @Test
    void validarConteudo_tiffComAsQuatroAssinaturas_deveAceitar() throws IOException {
        byte[][] assinaturas = {
                {'I', 'I', '*', 0x00},
                {'M', 'M', 0x00, '*'},
                {'I', 'I', '+', 0x00},
                {'M', 'M', 0x00, '+'}
        };
        for (byte[] assinatura : assinaturas) {
            Path arquivo = arquivo(assinatura);
            assertDoesNotThrow(() -> validador.validarConteudo(arquivo, "TIF"));
        }
    }

    @Test
    void validarConteudo_tiffSemAssinatura_deveRejeitar() throws IOException {
        Path arquivo = arquivo("abcd".getBytes(StandardCharsets.US_ASCII));

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "TIF"));
    }

    // ------------------------------------------------------------------- GeoJSON

    @Test
    void validarConteudo_geojsonComObjeto_deveAceitar() throws IOException {
        Path arquivo = arquivo("{\"type\":\"FeatureCollection\",\"features\":[]}".getBytes(StandardCharsets.UTF_8));

        assertDoesNotThrow(() -> validador.validarConteudo(arquivo, "GEOJSON"));
    }

    @Test
    void validarConteudo_geojsonComBomEEspacosIniciais_deveAceitar() throws IOException {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] corpo = "  \n\t{\"type\":\"Point\"}".getBytes(StandardCharsets.UTF_8);
        byte[] conteudo = new byte[bom.length + corpo.length];
        System.arraycopy(bom, 0, conteudo, 0, bom.length);
        System.arraycopy(corpo, 0, conteudo, bom.length, corpo.length);
        Path arquivo = arquivo(conteudo);

        assertDoesNotThrow(() -> validador.validarConteudo(arquivo, "GEOJSON"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,2,3]", "<xml/>", "", "   \n  "})
    void validarConteudo_geojsonQueNaoComecaComChave_deveRejeitar(String texto) throws IOException {
        Path arquivo = arquivo(texto.getBytes(StandardCharsets.UTF_8));

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "GEOJSON"));
    }

    // ----------------------------------------------------------------------- CSV

    @Test
    void validarConteudo_csvDeTexto_deveAceitar() throws IOException {
        Path arquivo = arquivo("id;nome\n1;João\n2;Maria\n".getBytes(StandardCharsets.ISO_8859_1));

        assertDoesNotThrow(() -> validador.validarConteudo(arquivo, "CSV"));
    }

    @Test
    void validarConteudo_csvComByteNulo_deveRejeitar() throws IOException {
        Path arquivo = arquivo(new byte[] {'i', 'd', ',', 0x00, 'x'});

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "CSV"));
    }

    // ------------------------------------------------------------- casos gerais

    @Test
    void validarConteudo_arquivoInexistente_deveRejeitar() {
        Path inexistente = pasta.resolve("nao-existe.csv");

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(inexistente, "CSV"));
    }

    @Test
    void validarConteudo_caminhoNulo_deveRejeitar() {
        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(null, "CSV"));
    }

    @Test
    void validarConteudo_formatoDesconhecido_deveRejeitar() throws IOException {
        Path arquivo = arquivo("x".getBytes(StandardCharsets.UTF_8));

        assertThrows(RequisicaoInvalidaException.class, () -> validador.validarConteudo(arquivo, "XYZ"));
    }

    // ------------------------------------------------------------------ auxiliares

    private Path arquivo(byte[] conteudo) throws IOException {
        Path arquivo = Files.createTempFile(pasta, "teste-", ".bin");
        Files.write(arquivo, conteudo);
        return arquivo;
    }

    private Path zip(String... entradas) throws IOException {
        Path arquivo = Files.createTempFile(pasta, "teste-", ".zip");
        try (ZipOutputStream saida = new ZipOutputStream(Files.newOutputStream(arquivo))) {
            for (String entrada : entradas) {
                saida.putNextEntry(new ZipEntry(entrada));
                saida.write("x".getBytes(StandardCharsets.UTF_8));
                saida.closeEntry();
            }
        }
        return arquivo;
    }
}
