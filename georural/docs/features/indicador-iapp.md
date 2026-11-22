# Indicador IAPP — Índice de Área de Preservação Permanente Conservada

> **História de Usuário:** US#23  
> **Sigla no Catálogo (`INDICADOR`):** `IAPP`  
> **Unidade:** `%` (percentual) e `ha` (hectares)  
> **Sentido de Análise:** Quanto maior o `iapp_pct` (e menor o `passivo_ha`), melhor a situação ambiental do imóvel.

---

## 1. Regras de Cálculo e Critérios Técnicos (US#23.01)

### 1.1. Definição e Fórmulas Matemáticas
O **IAPP** mede a proporção da Área de Preservação Permanente (APP) dentro do perímetro do imóvel rural que se encontra coberta por vegetação nativa, bem como o passivo ambiental em hectares a ser recomposto:

$$\text{IAPP (\%)} = \frac{\text{app\_vegetada\_ha}}{\text{app\_total\_ha}} \times 100$$

$$\text{passivo\_ha} = \text{app\_total\_ha} - \text{app\_vegetada\_ha}$$

Onde:
* **`app_total_ha`**: área (em hectares) resultante do recorte da camada dissolvida de APP pelo perímetro do imóvel rural (`APP_dissolvida ∩ IMOVEL_RURAL`).
* **`app_vegetada_ha`**: área (em hectares) resultante da interseção entre a APP recortada no imóvel e a camada dissolvida de vegetação nativa (`APP_imovel ∩ VEGETACAO_NATIVA_dissolvida`), limitada superiormente a `app_total_ha` (`LEAST(..., app_total_ha)`) para evitar distorções de precisão numérica.
* **`passivo_ha`**: área (em hectares) de APP desprovida de vegetação nativa dentro do imóvel (`ROUND(app_total_ha - app_vegetada_ha, 4)`), sempre $\ge 0$.
* **`iapp_pct`**: percentual de conservação da APP no imóvel, arredondado em 4 casas decimais e limitado ao intervalo $[0, 100]$.

---

### 1.2. Sistema de Referência Espacial e Cálculo de Área
* **SRID Oficial:** `4674` (**SIRGAS 2000** — coordenadas geográficas em graus decimais, padrão adotado em todo o projeto e em `USER_SDO_GEOM_METADATA`)[cite: 1, 6].
* **Tolerância Espacial:** `0.05` metros (`PKG_GEO.c_tol`), idêntica às operações de `PKG_GEO` e `PKG_ISAP`[cite: 1].
* **Transformação de SRID:** Caso alguma geometria de entrada possua SRID distinto de `4674` (e não nulo), deve ser reprojetada via `SDO_CS.TRANSFORM(geom, 4674)` antes das operações topológicas[cite: 6].
* **Cálculo Geodésico de Área:** Todas as áreas são calculadas diretamente no Oracle Spatial em hectares via `PKG_GEO.area_ha` (`SDO_GEOM.SDO_AREA(geom, 0.05, 'unit=HECTARE')`), nunca por soma simples de atributos declarados em shapefile/GeoJSON[cite: 6].

---

### 1.3. Preparação Espacial, Dissolução e Ordem de Cruzamento
Para evitar **dupla contagem** de áreas sobrepostas (ex.: dois polígonos de APP de nascente e curso d'água que se cruzam, ou fragmentos adjacentes/sobrepostos de vegetação nativa):
1. **Validação e Saneamento:** Geometrias de `IMOVEL_RURAL`, `APP` e `VEGETACAO_NATIVA` são verificadas com `PKG_GEO.valida` (`SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT`) e, quando inválido-corrigíveis, saneadas via `PKG_GEO.saneada` (`SDO_UTIL.RECTIFY_GEOMETRY`)[cite: 1, 6]. Geometrias inválidas irrecuperáveis são registradas em `QUARENTENA` sem interromper o processamento em lote[cite: 6].
2. **Dissolução Intra-Camada (`SDO_AGGR_UNION`):**[cite: 6]
   * As feições elegíveis de `APP` da versão de referência são dissolvidas por versão e filtro aplicado (`APP_DISSOLVIDA`).
   * As feições elegíveis de `VEGETACAO_NATIVA` da versão de referência são dissolvidas por versão e filtro aplicado (`VEGETACAO_DISSOLVIDA`).
3. **Recorte pelo Perímetro do Imóvel:**[cite: 6]
   * A APP dissolvida é recortada pelo polígono do imóvel (`v_app_imovel := PKG_GEO.intersecao(v_imo_geom, v_app_dissolvida)`)[cite: 6].
   * Em seguida, a APP recortada é cruzada com a vegetação nativa dissolvida (`v_app_veg := PKG_GEO.intersecao(v_app_imovel, v_veg_dissolvida)`)[cite: 6].

---

### 1.4. Parâmetros Configuráveis da Regra (`PARAMETRO_REGRA_CALCULO`)
A regra `v1` do indicador `IAPP` suporta os seguintes parâmetros editáveis (protegidos por imutabilidade após uso em execução publicada)[cite: 1, 3]:

| Parâmetro (`prc_nome`) | Tipo | Valor Padrão | Descrição |
| :--- | :--- | :--- | :--- |
| `tipos_app_incluidos` | `TEXTO` | `*` | Lista separada por vírgula dos tipos de APP considerados no cálculo (ex.: `CURSO_DAGUA,NASCENTE,TOPO_MORRO`). `*` considera todos os tipos. |
| `classes_vegetacao_incluidas` | `TEXTO` | `*` | Lista separada por vírgula das classes de vegetação nativa consideradas (ex.: `FLORESTA,CERRADO`). `*` considera todas as classes. |
| `minimo_iapp_conforme_pct` | `PERCENTUAL` | `100` | Percentual mínimo de APP vegetada para considerar o imóvel **CONFORME** na agregação municipal (`100` equivale a `passivo_ha = 0`). |

---

### 1.5. Casos de Borda e Regras de Negócio Específicas
1. **APP Totalmente Vegetada:**
   * `app_vegetada_ha = app_total_ha` $\implies$ `iapp_pct = 100.0000`, `passivo_ha = 0.0000`, `status = 'CALCULADO'`[cite: 6].
2. **APP Parcialmente Vegetada:**
   * $0 < \text{app\_vegetada\_ha} < \text{app\_total\_ha} \implies 0 < \text{iapp\_pct} < 100$, `passivo_ha = ROUND(app_total_ha - app_vegetada_ha, 4)`, `status = 'CALCULADO'`[cite: 6].
3. **APP Sem Vegetação Nativa (Passivo Integral):**
   * Quando há APP no imóvel (`app_total_ha > 0`), mas não há interseção com vegetação nativa (`app_vegetada_ha = 0`)[cite: 6]:
   * `app_vegetada_ha = 0.0000`, `iapp_pct = 0.0000`, `passivo_ha = app_total_ha`, `status = 'CALCULADO'`[cite: 6].
4. **Imóvel Sem APP (`SEM_APP`):**
   * Quando o imóvel não possui feições de APP incidentes ou `app_total_ha = 0` (após recorte e filtros), mas ambas as camadas (`APP` e `VEGETACAO_NATIVA`) possuem versão publicada disponível[cite: 6]:
   * Evita-se divisão por zero (`NULLIF(app_total_ha, 0)`)[cite: 6];
   * `status = 'SEM_APP'`; `app_total_ha = NULL`, `app_vegetada_ha = NULL`, `passivo_ha = NULL`, `iapp_pct = NULL`[cite: 6].
5. **Camada Indisponível (`CAMADA_INDISPONIVEL`):**
   * Quando não existe versão publicada/vigente de `APP` ou de `VEGETACAO_NATIVA` (ou nenhuma feição válida após filtro na camada)[cite: 6]:
   * `status = 'CAMADA_INDISPONIVEL'`; `app_total_ha = NULL`, `app_vegetada_ha = NULL`, `passivo_ha = NULL`, `iapp_pct = NULL`[cite: 6].

---

## 2. Contrato de Saída do IAPP (US#23.02)

### 2.1. Estados de Saída (`status`) e Regras de Nulabilidade

| `status` | Condição de Ocorrência | Campos Numéricos (`appTotalHa`, `appVegetadaHa`, `passivoHa`, `iappPct`) | Geometrias (`appVegetadaGeoJson`, `passivoGeoJson`) | `mensagem` (Exemplo) |
| :--- | :--- | :---: | :---: | :--- |
| `CALCULADO` | Camadas `APP` e `VEGETACAO_NATIVA` publicadas e `app_total_ha > 0` no imóvel | Preenchidos (`>= 0`) | Preenchidas (ou `null` se feição vazia) | `"Cálculo concluído com sucesso."` ou `"APP sem cobertura de vegetação nativa (passivo integral)."` |
| `SEM_APP` | Camadas publicadas, porém o imóvel não possui APP incidente (`app_total_ha = 0`) | `NULL` | `NULL` | `"O imóvel não possui Área de Preservação Permanente (APP) incidente na versão avaliada."` |
| `CAMADA_INDISPONIVEL` | Não há versão publicada/vigente de `APP` ou de `VEGETACAO_NATIVA` | `NULL` | `NULL` | `"Camada de APP ou Vegetação Nativa indisponível/não publicada para o cálculo."` |

---

### 2.2. Mapeamento dos Campos (JSON REST x Oracle `RESULTADO_IAPP`)

| Campo JSON | Coluna Oracle | Tipo | Nulável | Descrição |
| :--- | :--- | :--- | :---: | :--- |
| `appTotalHa` | `iap_app_total_ha` | `BigDecimal` (`NUMBER(14,4)`) | Sim | Área total de APP recortada dentro do imóvel, em hectares (`> 0` quando `CALCULADO`; `null` nos demais status). |
| `appVegetadaHa` | `iap_app_vegetada_ha` | `BigDecimal` (`NUMBER(14,4)`) | Sim | Área de APP coberta por vegetação nativa, em hectares (`0 <= appVegetadaHa <= appTotalHa`). |
| `passivoHa` | `iap_passivo_ha` | `BigDecimal` (`NUMBER(14,4)`) | Sim | Área de passivo ambiental a recuperar (`appTotalHa - appVegetadaHa`), em hectares. |
| `iappPct` | `iap_iapp_pct` | `BigDecimal` (`NUMBER(7,4)`) | Sim | Percentual de APP conservada (`0` a `100`). |
| `status` | `iap_status` | `String` (Enum) | Não | `CALCULADO`, `SEM_APP` ou `CAMADA_INDISPONIVEL`. |
| `mensagem` | `iap_mensagem` | `String` (`VARCHAR2(500)`) | Não | Explicação textual legível do resultado ou motivo de ausência/indisponibilidade. |
| `conforme` | *(derivado)* | `Boolean` | Sim | `true` se `status == CALCULADO` e `iappPct >= minimo_iapp_conforme_pct` (padrão `100%`, i.e., `passivoHa == 0`); `false` se `CALCULADO` e abaixo do mínimo; `null` se `SEM_APP` ou `CAMADA_INDISPONIVEL`. |
| `filtroTiposApp` | `iap_filtro_tipos_app` | `String` (`VARCHAR2(500)`) | Sim | Filtro de tipos de APP aplicado na execução (ex.: `'*'` ou `'CURSO_DAGUA,NASCENTE'`). |
| `filtroClassesVegetacao` | `iap_filtro_classes_veg` | `String` (`VARCHAR2(500)`) | Sim | Filtro de classes de vegetação nativa aplicado (ex.: `'*'` ou `'FLORESTA,CERRADO'`). |
| `appVegetadaGeoJson` | `iap_app_vegetada_geom` | `Object` (`@JsonRawValue`) | Sim | Geometria GeoJSON (SRID `4674`) da porção de APP coberta por vegetação nativa no imóvel (retornado na consulta detalhada por imóvel). |
| `passivoGeoJson` | `iap_passivo_geom` | `Object` (`@JsonRawValue`) | Sim | Geometria GeoJSON (SRID `4674`) da porção de APP sem vegetação nativa (`APP_imovel \ VEGETACAO_NATIVA`) para destaque de passivo no mapa. |

---

### 2.3. Critério de Conformidade no Consolidado Municipal (`ConsolidadoMunicipioService`)
* **Métrica de agregação municipal (`RESULTADO_MUNICIPIO`):**
  * `rmu_valor_agregado`: média aritmética de `iap_iapp_pct` entre os imóveis do município com `iap_status = 'CALCULADO'`.
  * `soma_hectares` (em `rmu_detalhes`): soma de `iap_passivo_ha` (passivo total de APP a recuperar no município) e `soma_app_vegetada_ha`.
  * `conformes` (em `rmu_detalhes`): quantidade de imóveis com `iap_status = 'CALCULADO'` cuja condição de conformidade seja atendida (`iap_iapp_pct >= minimo_iapp_conforme_pct`, sendo o padrão `100%`, ou seja, `iap_passivo_ha = 0`).
  * Imóveis com `SEM_APP` ou `CAMADA_INDISPONIVEL` não entram como "não conformes" nem distorcem a média percentual do município.

---

### 2.4. Exemplos de Payload JSON (`GET /api/v1/iapp/imoveis/{imovelId}`)

#### Cenário A — `CALCULADO` (Passivo Parcial com GeoJSON para o Mapa)
```json
{
  "imovelId": 201,
  "codigoCar": "PR-4106902-9F8E7D6C5B4A3210FEDC",
  "nomeImovel": "Fazenda Ribeirão Claro",
  "municipio": "Curitiba",
  "areaImovelHa": 120.0000,
  "versaoId": 12,
  "versaoNumero": 3,
  "regraVersao": "v1",
  "dataCalculo": "2026-11-22T16:20:00",
  "appTotalHa": 20.0000,
  "appVegetadaHa": 15.0000,
  "passivoHa": 5.0000,
  "iappPct": 75.0000,
  "conforme": false,
  "status": "CALCULADO",
  "mensagem": "Cálculo concluído com sucesso. Passivo ambiental de 5,0000 ha identificado em APP.",
  "filtroTiposApp": "*",
  "filtroClassesVegetacao": "*",
  "rastreabilidade": {
    "versaoAppId": 18,
    "competenciaApp": "2026-10",
    "versaoVegetacaoId": 19,
    "competenciaVegetacao": "2026-10",
    "execucaoId": 94,
    "regraId": 4,
    "regraVersao": "v1"
  },
  "appVegetadaGeoJson": {
    "type": "Polygon",
    "coordinates": [[[-51.02, -25.10], [-51.01, -25.10], [-51.01, -25.095], [-51.02, -25.095], [-51.02, -25.10]]]
  },
  "passivoGeoJson": {
    "type": "Polygon",
    "coordinates": [[[-51.02, -25.095], [-51.01, -25.095], [-51.01, -25.09], [-51.02, -25.09], [-51.02, -25.095]]]
  }
}
```
#### Cenário B — SEM_APP
```json
{
  "imovelId": 202,
  "codigoCar": "PR-4106902-00000000000000000001",
  "nomeImovel": "Sítio Espigão Alto",
  "municipio": "Curitiba",
  "areaImovelHa": 45.5000,
  "versaoId": 12,
  "versaoNumero": 3,
  "regraVersao": "v1",
  "dataCalculo": "2026-11-22T16:20:01",
  "appTotalHa": null,
  "appVegetadaHa": null,
  "passivoHa": null,
  "iappPct": null,
  "conforme": null,
  "status": "SEM_APP",
  "mensagem": "O imóvel não possui Área de Preservação Permanente (APP) incidente na versão avaliada.",
  "filtroTiposApp": "*",
  "filtroClassesVegetacao": "*",
  "rastreabilidade": {
    "versaoAppId": 18,
    "competenciaApp": "2026-10",
    "versaoVegetacaoId": 19,
    "competenciaVegetacao": "2026-10",
    "execucaoId": 94,
    "regraId": 4,
    "regraVersao": "v1"
  },
  "appVegetadaGeoJson": null,
  "passivoGeoJson": null
}
```

#### Cenário C — CAMADA_INDISPONIVEL
```json
{
  "imovelId": 203,
  "codigoCar": "PR-4106902-00000000000000000002",
  "nomeImovel": "Chácara Vale Verde",
  "municipio": "Curitiba",
  "areaImovelHa": 30.0000,
  "versaoId": 12,
  "versaoNumero": 3,
  "regraVersao": "v1",
  "dataCalculo": "2026-11-22T16:20:02",
  "appTotalHa": null,
  "appVegetadaHa": null,
  "passivoHa": null,
  "iappPct": null,
  "conforme": null,
  "status": "CAMADA_INDISPONIVEL",
  "mensagem": "Camada de APP ou Vegetação Nativa indisponível/não publicada para o cálculo.",
  "filtroTiposApp": "*",
  "filtroClassesVegetacao": "*",
  "rastreabilidade": {
    "versaoAppId": 18,
    "competenciaApp": "2026-10",
    "versaoVegetacaoId": null,
    "competenciaVegetacao": null,
    "execucaoId": 94,
    "regraId": 4,
    "regraVersao": "v1"
  },
  "appVegetadaGeoJson": null,
  "passivoGeoJson": null
}
```

---

## 3. Massa de Teste e Gabarito Calculado à Mão (US#23.03)

Script versionado: `src/main/resources/db/test/seed_teste_iapp.sql`  
Na latitude $\varphi \approx -25{,}19^\circ$ (SIRGAS 2000), cada quadrado base de $0{,}02^\circ \times 0{,}02^\circ$ possui área $A_{\text{imóvel}} \approx 446{,}60\text{ ha}$ (cada quadrante $0{,}01^\circ \times 0{,}01^\circ \approx 111{,}65\text{ ha}$).

| Código CAR | Cenário | `status` | `app_total_ha` | `app_vegetada_ha` | `passivo_ha` | `iapp_pct` | `conforme` |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| `PR-TESTE-IAPP-01` | APP totalmente vegetada (metade sul do imóvel 100% coberta) | `CALCULADO` | $\frac{1}{2} A_{\text{imóvel}}$ ($\approx 223{,}30$) | $\frac{1}{2} A_{\text{imóvel}}$ ($\approx 223{,}30$) | `0.0000` | `100.0000` | `true` |
| `PR-TESTE-IAPP-02` | APP parcialmente vegetada (4 quadrantes de APP, 3 de vegetação) | `CALCULADO` | $A_{\text{imóvel}}$ ($\approx 446{,}60$) | $\frac{3}{4} A_{\text{imóvel}}$ ($\approx 334{,}95$) | $\frac{1}{4} A_{\text{imóvel}}$ ($\approx 111{,}65$) | `75.0000` | `false` |
| `PR-TESTE-IAPP-03` | APP sem vegetação nativa (passivo integral) | `CALCULADO` | $\frac{1}{2} A_{\text{imóvel}}$ ($\approx 223{,}30$) | `0.0000` | $\frac{1}{2} A_{\text{imóvel}}$ ($\approx 223{,}30$) | `0.0000` | `false` |
| `PR-TESTE-IAPP-04` | Imóvel sem APP incidente | `SEM_APP` | `NULL` | `NULL` | `NULL` | `NULL` | `NULL` |
| `PR-TESTE-IAPP-05` | APPs sobrepostas (`A ∪ B`) + trecho fora do imóvel recortado ($3/4$ do imóvel; $2/4$ vegetados) | `CALCULADO` | $\frac{3}{4} A_{\text{imóvel}}$ ($\approx 334{,}95$) | $\frac{2}{4} A_{\text{imóvel}}$ ($\approx 223{,}30$) | $\frac{1}{4} A_{\text{imóvel}}$ ($\approx 111{,}65$) | `66.6667` | `false` |