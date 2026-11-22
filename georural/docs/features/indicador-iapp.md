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