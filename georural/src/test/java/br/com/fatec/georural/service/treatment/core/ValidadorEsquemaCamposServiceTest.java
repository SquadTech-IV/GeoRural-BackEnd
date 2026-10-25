package br.com.fatec.georural.service.treatment.core;

import br.com.fatec.georural.entity.TipoConjunto;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ValidadorEsquemaCamposServiceTest {

    @InjectMocks
    private ValidadorEsquemaCamposService validador;

    @Mock
    private BaseTreatment context;

    private TipoConjunto tipoConjunto;

    @BeforeEach
    void setUp() {
        tipoConjunto = new TipoConjunto();
        tipoConjunto.setEsquema("""
            {
              "properties": {
                "num_tad": { "type": "string" },
                "area_ha": { "type": "number", "minimum": 0 },
                "situacao": { "type": "string", "enum": ["ATIVO", "PENDENTE"] }
              },
              "required": ["num_tad"]
            }
            """);
    }

    @Test
    @DisplayName("VAL-001: Deve aprovar registro com campos obrigatórios preenchidos")
    void deveAprovarCampoObrigatorio() {
        SimpleFeature feature = criarFeature("TAD-123", 10.5, "ATIVO");
        boolean resultado = validador.validarRegistro(tipoConjunto, feature, context);
        assertTrue(resultado);
    }

    @Test
    @DisplayName("VAL-001: Deve rejeitar registro com campo obrigatório ausente")
    void deveRejeitarCampoObrigatorioAusente() {
        SimpleFeature feature = criarFeature(null, 10.5, "ATIVO");
        boolean resultado = validador.validarRegistro(tipoConjunto, feature, context);
        assertFalse(resultado);
        verify(context).rejeitar(eq("CAMPO"), eq("num_tad"), anyString(), eq(feature));
    }

    @Test
    @DisplayName("VAL-002: Deve rejeitar tipo incompatível em campo numérico")
    void deveRejeitarTipoIncompativel() {
        SimpleFeature feature = criarFeature("TAD-123", "TEXTO_INVALIDO", "ATIVO");
        boolean resultado = validador.validarRegistro(tipoConjunto, feature, context);
        assertFalse(resultado);
        verify(context).rejeitar(eq("TIPO"), eq("area_ha"), anyString(), eq(feature));
    }

    @Test
    @DisplayName("VAL-003: Deve rejeitar valor fora do domínio de enum permitido")
    void deveRejeitarDominioInvalido() {
        SimpleFeature feature = criarFeature("TAD-123", 10.5, "VALOR_INEXISTENTE");
        boolean resultado = validador.validarRegistro(tipoConjunto, feature, context);
        assertFalse(resultado);
        verify(context).rejeitar(eq("DOMINIO"), eq("situacao"), anyString(), eq(feature));
    }

    private SimpleFeature criarFeature(Object numTad, Object area, String situacao) {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("FeatureTeste");
        typeBuilder.add("num_tad", String.class);
        typeBuilder.add("area_ha", Object.class);
        typeBuilder.add("situacao", String.class);

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(typeBuilder.buildFeatureType());
        builder.add(numTad);
        builder.add(area);
        builder.add(situacao);
        return builder.buildFeature("1");
    }
}