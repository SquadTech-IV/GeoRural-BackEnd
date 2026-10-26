package br.com.fatec.georural.service.treatment.core;

import br.com.fatec.georural.entity.TipoConjunto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.feature.simple.SimpleFeature;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Serviço responsável por aplicar as regras de validação de esquema da US#08 (T05):
 * - VAL-001 (CAMPO): Campos Obrigatórios
 * - VAL-002 (TIPO): Tipos de Dados
 * - VAL-003 (DOMINIO): Domínio de Valores
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidadorEsquemaCamposService {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Valida um registro em relação ao esquema do TipoConjunto.
     * Registra em quarentena os campos reprovados.
     *
     * @return true se o registro for 100% válido nas regras de esquema; false caso contrário.
     */
    public boolean validarRegistro(TipoConjunto tipoConjunto, SimpleFeature feature, BaseTreatment context) {
        if (tipoConjunto == null || tipoConjunto.getEsquema() == null || tipoConjunto.getEsquema().isBlank()) {
            return true;
        }

        try {
            JsonNode esquemaNode = mapper.readTree(tipoConjunto.getEsquema());
            return processarEsquema(esquemaNode, feature, context);
        } catch (Exception e) {
            log.error("Erro ao processar esquema do TipoConjunto id={}: {}", tipoConjunto.getId(), e.getMessage());
            return true;
        }
    }

    private boolean processarEsquema(JsonNode esquemaNode, SimpleFeature feature, BaseTreatment context) {
        boolean valido = true;

        List<String> requiredList = new ArrayList<>();
        Map<String, JsonNode> propsMap = new LinkedHashMap<>();

        if (esquemaNode.has("properties")) {
            JsonNode props = esquemaNode.get("properties");
            props.fieldNames().forEachRemaining(fn -> propsMap.put(fn, props.get(fn)));
            if (esquemaNode.has("required") && esquemaNode.get("required").isArray()) {
                esquemaNode.get("required").forEach(r -> requiredList.add(r.asText()));
            }
        } else if (esquemaNode.has("campos") && esquemaNode.get("campos").isArray()) {
            for (JsonNode c : esquemaNode.get("campos")) {
                String nome = c.has("nome") ? c.get("nome").asText() : c.has("campo") ? c.get("campo").asText() : null;
                if (nome != null) {
                    propsMap.put(nome, c);
                    if (c.has("obrigatorio") && c.get("obrigatorio").asBoolean()) {
                        requiredList.add(nome);
                    }
                }
            }
        } else if (esquemaNode.isArray()) {
            for (JsonNode c : esquemaNode) {
                String nome = c.has("nome") ? c.get("nome").asText() : c.has("campo") ? c.get("campo").asText() : null;
                if (nome != null) {
                    propsMap.put(nome, c);
                    if (c.has("obrigatorio") && c.get("obrigatorio").asBoolean()) {
                        requiredList.add(nome);
                    }
                }
            }
        }

        for (Map.Entry<String, JsonNode> entry : propsMap.entrySet()) {
            String campo = entry.getKey();
            JsonNode campoDef = entry.getValue();
            boolean isObrigatorio = requiredList.contains(campo) || 
                    (campoDef.has("obrigatorio") && campoDef.get("obrigatorio").asBoolean());

            Object valorObj = feature.getAttribute(campo);

            // 1. VAL-001 (CAMPO) - Campos Obrigatórios
            if (isObrigatorio && (valorObj == null || (valorObj instanceof String s && s.isBlank()))) {
                context.rejeitar("CAMPO", campo, "Campo obrigatório ausente ou nulo: " + campo, feature);
                valido = false;
                continue;
            }

            if (valorObj == null) {
                continue;
            }

            // 2. VAL-002 (TIPO) - Tipos de Dados
            String tipoEsperado = extrairTipoEsperado(campoDef);
            if (tipoEsperado != null && !validarTipoDado(valorObj, tipoEsperado)) {
                context.rejeitar("TIPO", campo, 
                        String.format("Valor do campo '%s' incompatível com o tipo '%s'", campo, tipoEsperado), feature);
                valido = false;
                continue;
            }

            // 3. VAL-003 (DOMINIO) - Domínio de Valores
            if (!validarDominio(valorObj, campoDef)) {
                context.rejeitar("DOMINIO", campo, 
                        String.format("Valor '%s' fora do domínio permitido para o campo '%s'", valorObj, campo), feature);
                valido = false;
            }
        }

        return valido;
    }

    private String extrairTipoEsperado(JsonNode campoDef) {
        if (campoDef.has("tipo")) {
            return campoDef.get("tipo").asText().toUpperCase();
        } else if (campoDef.has("type")) {
            return campoDef.get("type").asText().toUpperCase();
        }
        return null;
    }

    private boolean validarTipoDado(Object valor, String tipoEsperado) {
        String strVal = valor.toString().trim();
        return switch (tipoEsperado) {
            case "NUMBER", "NUMERIC", "DOUBLE", "FLOAT", "DECIMAL" -> {
                if (valor instanceof Number) yield true;
                try {
                    Double.parseDouble(strVal);
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "INTEGER", "INT", "LONG" -> {
                if (valor instanceof Integer || valor instanceof Long) yield true;
                try {
                    Long.parseLong(strVal);
                    yield true;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case "BOOLEAN", "BOOL" -> {
                if (valor instanceof Boolean) yield true;
                yield "true".equalsIgnoreCase(strVal) || "false".equalsIgnoreCase(strVal);
            }
            case "STRING", "TEXT", "VARCHAR" -> true;
            default -> true;
        };
    }

    private boolean validarDominio(Object valor, JsonNode campoDef) {
        String strVal = valor.toString().trim();

        // Enum / Lista de valores aceitos
        JsonNode enumNode = campoDef.has("enum") ? campoDef.get("enum") : campoDef.get("dominio");
        if (enumNode != null && enumNode.isArray() && enumNode.size() > 0) {
            boolean encontrado = false;
            for (JsonNode item : enumNode) {
                if (item.asText().equalsIgnoreCase(strVal)) {
                    encontrado = true;
                    break;
                }
            }
            if (!encontrado) return false;
        }

        // Padrão Regex (pattern)
        if (campoDef.has("pattern")) {
            String pattern = campoDef.get("pattern").asText();
            if (!strVal.matches(pattern)) {
                return false;
            }
        }

        // Limites numéricos
        if (valor instanceof Number num) {
            double dVal = num.doubleValue();
            if (campoDef.has("minimum") && dVal < campoDef.get("minimum").asDouble()) {
                return false;
            }
            if (campoDef.has("maximum") && dVal > campoDef.get("maximum").asDouble()) {
                return false;
            }
        }

        return true;
    }
}