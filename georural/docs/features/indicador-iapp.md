# Indicador IAPP — Índice de Área de Preservação Permanente Conservada

> **História de Usuário:** US#23  
> **Sigla no Catálogo (`INDICADOR`):** `IAPP`  
> **Unidade:** `%` (percentual) e `ha` (hectares)  
> **Sentido de Análise:** `MAIOR_MELHOR` — Quanto maior o `iapp_pct` (e menor o `passivo_ha`), melhor a situação ambiental do imóvel.

---

## 1. Regras de Cálculo e Critérios Técnicos (US#23.01)

### 1.1. Definição e Fórmulas Matemáticas
O **IAPP** mede a proporção da Área de Preservação Permanente (APP) dentro do perímetro do imóvel rural que se encontra coberta por vegetação nativa, bem como o passivo ambiental em hectares a ser recomposto:

$$\text{IAPP (\%)} = \frac{\text{app\_vegetada\_ha}}{\text{app\_total\_ha}} \times 100$$

$$\text{passivo\_ha} = \text{app\_total\_ha} - \text{app\_vegetada\_ha}$$

Onde:
- **`app_total_ha`**: área (em hectares) resultante do recorte da camada dissolvida de APP pelo perímetro do imóvel rural (`APP_dissolvida ∩ IMOVEL_RURAL`).
- **`app_vegetada_ha`**: área (em hectares) resultante da interseção entre a APP recortada no imóvel e a camada dissolvida de vegetação nativa (`APP_imovel ∩ VEGETACAO_NATIVA_dissolvida`), limitada superiormente a `app_total_ha` (`LEAST(..., app_total_ha)`) para evitar distorções de precisão numérica.
- **`passivo_ha`**: área (em hectares) de APP desprovida de vegetação nativa dentro do imóvel (`ROUND(app_total_ha - app_vegetada_ha, 4)`), sempre $\ge 0$.
- **`iapp_pct`**: percentual de conservação da APP no imóvel, arredondado em 4 casas decimais e limitado ao intervalo $[0, 100]$.

### 1.2. Sistema de Referência Espacial e Cálculo de Área
- **SRID Oficial:** `4674` (**SIRGAS 2000** — coordenadas geográficas em graus decimais, padrão adotado em todo o projeto e em `USER_SDO_GEOM_METADATA`).
- **Tolerância Espacial:** `0.05` metros (`PKG_GEO.c_tol`), idêntica às operações de `PKG_GEO`, `PKG_CALCULO` e `PKG_ISAP`.
- **Transformação de SRID:** Caso alguma geometria de entrada possua SRID distinto de `4674` (e não nulo), é reprojetada via `SDO_CS.TRANSFORM(geom, 4674)` antes das operações topológicas (`PKG_IAPP.garantir_srid_4674`). Na exportação GeoJSON pela API REST (`incluirGeoJson=true`), as geometrias são entregues em WGS 84 (`SRID 4326`) via `SDO_UTIL.TO_GEOJSON(SDO_CS.TRANSFORM(..., 4326))`.
- **Cálculo Geodésico de Área:** Todas as áreas são calculadas diretamente no Oracle Spatial em hectares via `PKG_GEO.area_ha` (`SDO_GEOM.SDO_AREA(geom, 0.05, 'unit=HECTARE')`), nunca por soma simples de atributos declarados em shapefile/GeoJSON.

### 1.3. Preparação Espacial, Dissolução e Ordem de Cruzamento (US#23.05 e US#23.06)
Para evitar **dupla contagem** de áreas sobrepostas (ex.: dois polígonos de APP de nascente e curso d'água que se cruzam, ou fragmentos adjacentes/sobrepostos de vegetação nativa):
1. **Validação e Saneamento:** Geometrias de `IMOVEL_RURAL`, `APP` e `VEGETACAO_NATIVA` são verificadas com `PKG_GEO.valida` (`SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT`) e, quando inválido-corrigíveis, saneadas via `PKG_GEO.saneada` (`SDO_UTIL.RECTIFY_GEOMETRY`). Geometrias inválidas irrecuperáveis são registradas em `QUARENTENA` sem interromper o processamento em lote.
2. **Dissolução Intra-Camada (`SDO_AGGR_UNION`):**
   - As feições elegíveis de `APP` da versão de referência são dissolvidas por versão e filtro aplicado (`PKG_IAPP.dissolver_app`).
   - As feições elegíveis de `VEGETACAO_NATIVA` da versão de referência são dissolvidas por versão e filtro aplicado (`PKG_IAPP.dissolver_vegetacao`).
3. **Recorte pelo Perímetro do Imóvel:**
   - A APP dissolvida é recortada pelo polígono do imóvel (`v_app_imovel := PKG_GEO.intersecao(v_imo_geom, v_app_dissolvida)`).
   - Em seguida, a APP recortada é cruzada com a vegetação nativa dissolvida (`v_app_veg := PKG_GEO.intersecao(v_app_imovel, v_veg_dissolvida)`).
   - O passivo espacial é derivado pela diferença geométrica (`v_passivo := SDO_GEOM.SDO_DIFFERENCE(v_app_imovel, v_app_veg, 0.05)`).

### 1.4. Parâmetros Configuráveis da Regra (`PARAMETRO_REGRA_CALCULO`)
A regra `v1` do indicador `IAPP` possui os seguintes parâmetros configuráveis na tabela `PARAMETRO_REGRA_CALCULO`:

| Parâmetro (`prc_nome`) | Tipo | Valor Padrão | Descrição |
| :--- | :--- | :--- | :--- |
| `tipos_app_incluidos` | `TEXTO` | `*` | Lista separada por vírgula dos tipos de APP considerados no cálculo (ex.: `CURSO_DAGUA,NASCENTE,TOPO_MORRO`). `*` ou `TODOS` considera todos os tipos. |
| `classes_vegetacao_incluidas` | `TEXTO` | `*` | Lista separada por vírgula das classes de vegetação nativa consideradas (ex.: `FLORESTA,CERRADO`). `*` ou `TODOS` considera todas as classes. |
| `minimo_iapp_conforme_pct` | `PERCENTUAL` | `100` | Percentual mínimo de APP vegetada para considerar o imóvel **CONFORME** na agregação municipal (`100` equivale a `passivo_ha = 0`). |

### 1.5. Casos de Borda e Estados de Saída (`status` — US#23.02 e US#23.07)

| `status` (`iap_status`) | Condição de Ocorrência | `iap_app_total_ha` | `iap_app_vegetada_ha` | `iap_passivo_ha` | `iap_iapp_pct` | Geometrias (`appVegetadaGeoJson` / `passivoGeoJson`) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| `CALCULADO` | Camadas `APP` e `VEGETACAO_NATIVA` publicadas e `app_total_ha > 0` no imóvel | $> 0$ | $[0, \text{app\_total\_ha}]$ | $\text{app\_total\_ha} - \text{app\_vegetada\_ha}$ | $[0, 100]$ | Preenchidas (ou `null` se polígono vazio) |
| `SEM_APP` | Camadas publicadas, porém o imóvel não possui APP incidente (`app_total_ha = 0`) | `NULL` | `NULL` | `NULL` | `NULL` | `NULL` |
| `CAMADA_INDISPONIVEL` | Não há versão publicada/vigente de `APP` ou de `VEGETACAO_NATIVA` | `NULL` | `NULL` | `NULL` | `NULL` | `NULL` |

- **Proteção contra divisão por zero:** O cálculo percentual utiliza `NULLIF(v_app_total_ha, 0)` e só é executado quando `status = 'CALCULADO'`.
- **Critério de Conformidade Municipal (`ConsolidadoMunicipioService`):** Considera apenas imóveis com `iap_status = 'CALCULADO'`. Um imóvel é contabilizado como **conforme** quando `NVL(iap_passivo_ha, 0) = 0` (`iapp_pct = 100%`). Imóveis com `SEM_APP` ou `CAMADA_INDISPONIVEL` não entram no denominador de conformidade nem na média percentual municipal.

---

## 2. Dicionário de Dados — `RESULTADO_IAPP` (US#23.04 e US#23.08)

Criada pela migration `src/main/resources/db/migration/V11__modelo_iapp.sql` e calculada pela package `src/main/resources/db/plsql/pkg_iapp.sql` (orquestrada por `PKG_CALCULO.calcular_iapp` e `PKG_CALCULO.publicar`).

### 2.1. Colunas e Restrições da Tabela `RESULTADO_IAPP`

| Coluna | Tipo Oracle | Nulo | Padrão | Descrição / Restrições |
| :--- | :--- | :---: | :---: | :--- |
| `iap_id` | `NUMBER` | Não | `IDENTITY` | Chave primária (`pk_resultado_iapp`). |
| `imo_id` | `NUMBER` | Não | — | FK para `IMOVEL_RURAL(imo_id)` (`fk_iap_imovel`). Única por `(imo_id, ver_id)` (`uk_iap_imovel_versao`). |
| `ver_id` | `NUMBER` | Não | — | FK para `VERSAO(ver_id)` da rodada de cálculo (`fk_iap_versao`). |
| `iap_app_ver_id` | `NUMBER` | Sim | — | FK para `VERSAO(ver_id)` da camada `APP` utilizada (`fk_iap_app_versao`). |
| `iap_veg_ver_id` | `NUMBER` | Sim | — | FK para `VERSAO(ver_id)` da camada `VEGETACAO_NATIVA` utilizada (`fk_iap_veg_versao`). |
| `exe_id` | `NUMBER` | Sim | — | FK para `EXECUCAO_PIPELINE(exe_id)` que originou o cálculo (`fk_iap_execucao`). |
| `rca_id` | `NUMBER` | Sim | — | FK para `REGRA_CALCULO(rca_id)` aplicada (`fk_iap_regra`). |
| `iap_app_total_ha` | `NUMBER(14,4)` | Sim | — | Área total de APP recortada dentro do imóvel em hectares (`> 0` se `CALCULADO`; `NULL` caso contrário). |
| `iap_app_vegetada_ha` | `NUMBER(14,4)` | Sim | — | Área de APP coberta por vegetação nativa em hectares (`0 <= iap_app_vegetada_ha <= iap_app_total_ha`). |
| `iap_passivo_ha` | `NUMBER(14,4)` | Sim | — | Área de passivo ambiental (`ROUND(iap_app_total_ha - iap_app_vegetada_ha, 4)`), em hectares (`>= 0`). |
| `iap_iapp_pct` | `NUMBER(7,4)` | Sim | — | Percentual de APP conservada (`0` a `100`). |
| `iap_area_imovel_ha` | `NUMBER(14,4)` | Não | — | Área total do imóvel em hectares (`>= 0`). |
| `iap_filtro_tipos_app` | `VARCHAR2(500)` | Sim | — | Filtro de tipos de APP aplicado na execução (`'*'` ou lista delimitada). |
| `iap_filtro_classes_veg` | `VARCHAR2(500)` | Sim | — | Filtro de classes de vegetação nativa aplicado (`'*'` ou lista delimitada). |
| `iap_competencia_app` | `VARCHAR2(20)` | Sim | — | Competência (ex.: `'2026-10'`) da versão da camada `APP` utilizada. |
| `iap_competencia_veg` | `VARCHAR2(20)` | Sim | — | Competência (ex.: `'2026-10'`) da versão da camada `VEGETACAO_NATIVA` utilizada. |
| `iap_status` | `VARCHAR2(25)` | Não | — | Domínio: `'CALCULADO'`, `'SEM_APP'` ou `'CAMADA_INDISPONIVEL'` (`ck_iap_coerencia_status`). |
| `iap_mensagem` | `VARCHAR2(500)` | Não | — | Mensagem descritiva do resultado ou justificativa para `SEM_APP` / `CAMADA_INDISPONIVEL`. |
| `iap_app_vegetada_geom` | `SDO_GEOMETRY` | Sim | — | Geometria resultante da APP com vegetação nativa no imóvel (SRID `4674`, registrada em `USER_SDO_GEOM_METADATA` e indexada por `idx_iap_app_veg_spx`). |
| `iap_passivo_geom` | `SDO_GEOMETRY` | Sim | — | Geometria resultante do passivo ambiental de APP no imóvel (SRID `4674`, registrada em `USER_SDO_GEOM_METADATA` e indexada por `idx_iap_passivo_spx`). |
| `iap_vigente` | `CHAR(1)` | Não | `'N'` | `'S'` quando pertence à versão publicada vigente; `'N'` caso contrário (`ck_iap_vigente`). |
| `iap_data_calculo` | `TIMESTAMP` | Não | `SYSTIMESTAMP` | Data e hora da execução do cálculo. |

### 2.2. Constraints de Integridade (`V11__modelo_iapp.sql`)
- **`ck_iap_coerencia_status`**: Garante que:
  - Quando `iap_status = 'CALCULADO'`, todas as métricas (`iap_app_total_ha > 0`, `iap_app_vegetada_ha >= 0`, `iap_passivo_ha >= 0`, `iap_iapp_pct BETWEEN 0 AND 100`) são obrigatórias (`NOT NULL`);
  - Quando `iap_status IN ('SEM_APP', 'CAMADA_INDISPONIVEL')`, todas as métricas numéricas são obrigatoriamente `NULL`.
- **`ck_iap_identidade_areas`**: Garante `iap_app_vegetada_ha <= iap_app_total_ha` e `ABS((iap_app_total_ha - iap_app_vegetada_ha) - iap_passivo_ha) <= 0.0005`.

---

## 3. Contrato REST, Segurança e Integrações Transversais (US#23.09)

### 3.1. Endpoints REST (`/api/v1/iapp`)
Documentados via OpenAPI/Swagger (`/swagger-ui.html` e `/v3/api-docs`) sob a tag **`IAPP - APP Vegetada e Passivo Ambiental`** (`IappController.java`):

| Método | Rota | Descrição | Parâmetros |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/iapp` | Lista paginada (`PaginaResponse<IappListaItemResponse>`) dos resultados do IAPP (vigentes por padrão). | `status`, `municipioId`, `busca`, `versaoId`, `page`, `size`, `sort` |
| `GET` | `/api/v1/iapp/imoveis/{imovelId}` | Detalhamento completo (`IappResultadoResponse`) por ID interno do imóvel (`imo_id`). | `versaoId` (opcional), `incluirGeoJson` (`boolean`, padrão `false`) |
| `GET` | `/api/v1/iapp/imoveis/car/{codigoCar}` | Detalhamento completo (`IappResultadoResponse`) por código oficial do CAR. | `versaoId` (opcional), `incluirGeoJson` (`boolean`, padrão `false`) |

### 3.2. Controle de Acesso (RBAC — `SecurityConfig`)
- **Perfis autorizados (`GET /api/v1/iapp/**`):** `ADMIN`, `GESTOR`, `OPERADOR`, `ANALISTA` e `AUDITOR`.
- **Sem autenticação / JWT inválido:** retorna `401 Unauthorized` (registrado na auditoria de segurança).

### 3.3. Exemplos de Payload JSON (`GET /api/v1/iapp/imoveis/{imovelId}`)

#### Cenário A — `CALCULADO` (com `incluirGeoJson=true`)
```json
{
  "iappId": 100,
  "imovelId": 10,
  "codigoCar": "PR-4109401-IAPP01",
  "nomeImovel": "Fazenda Rio Verde",
  "municipioId": 4109401,
  "municipio": "Guarapuava",
  "versaoId": 3,
  "versaoNumero": 2,
  "appVersaoId": 4,
  "vegetacaoVersaoId": 5,
  "execucaoId": 12,
  "regraId": 8,
  "regraVersao": "v1",
  "appTotalHa": 20.0000,
  "appVegetadaHa": 15.0000,
  "passivoHa": 5.0000,
  "iappPct": 75.0000,
  "areaImovelHa": 100.0000,
  "filtroTiposApp": "TODOS",
  "filtroClassesVegetacao": "TODOS",
  "competenciaApp": "2026-10",
  "competenciaVegetacao": "2026-10",
  "status": "CALCULADO",
  "mensagem": "APP preservada em 75,00% (passivo de 5,0000 ha).",
  "vigente": "S",
  "dataCalculo": "2026-11-22T18:00:00",
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
  "iappId": 101,
  "imovelId": 11,
  "codigoCar": "PR-SEM-APP",
  "nomeImovel": "Sítio Campos",
  "municipioId": 4109401,
  "municipio": "Guarapuava",
  "versaoId": 3,
  "versaoNumero": 2,
  "appVersaoId": 4,
  "vegetacaoVersaoId": 5,
  "execucaoId": 12,
  "regraId": 8,
  "regraVersao": "v1",
  "appTotalHa": null,
  "appVegetadaHa": null,
  "passivoHa": null,
  "iappPct": null,
  "areaImovelHa": 50.0000,
  "filtroTiposApp": "TODOS",
  "filtroClassesVegetacao": "TODOS",
  "competenciaApp": "2026-10",
  "competenciaVegetacao": "2026-10",
  "status": "SEM_APP",
  "mensagem": "Imovel sem APP mapeada na versao vigente da camada APP.",
  "vigente": "S",
  "dataCalculo": "2026-11-22T18:05:00",
  "appVegetadaGeoJson": null,
  "passivoGeoJson": null
}
```
#### Cenário C — CAMADA_INDISPONIVEL
```json
{
  "iappId": 102,
  "imovelId": 12,
  "codigoCar": "PR-4109401-INDISP",
  "nomeImovel": "Fazenda Oeste",
  "municipioId": 4109401,
  "municipio": "Guarapuava",
  "versaoId": 3,
  "versaoNumero": 2,
  "appVersaoId": 4,
  "vegetacaoVersaoId": null,
  "execucaoId": 12,
  "regraId": 8,
  "regraVersao": "v1",
  "appTotalHa": null,
  "appVegetadaHa": null,
  "passivoHa": null,
  "iappPct": null,
  "areaImovelHa": 100.0000,
  "filtroTiposApp": "TODOS",
  "filtroClassesVegetacao": "TODOS",
  "competenciaApp": "2026-10",
  "competenciaVegetacao": null,
  "status": "CAMADA_INDISPONIVEL",
  "mensagem": "Camada de vegetacao nativa vigente indisponivel para o calculo do IAPP.",
  "vigente": "S",
  "dataCalculo": "2026-11-22T18:10:00",
  "appVegetadaGeoJson": null,
  "passivoGeoJson": null
}
```

### 3.4. Integrações Transversais no Back-EndPipeline (CalculoEtapa + CalculoOracle): Dispara 

PKG_CALCULO.calcular_iapp nos escopos IMOVEL, APP e VEGETACAO e registra o passo na trilha de transformação (RegistroTransformacaoService). A publicação de versão (PKG_CALCULO.publicar $\rightarrow$ PKG_ISAP.publicar / PKG_IAPP.publicar) promove os resultados para iap_vigente = 'S'.Recálculo Automático (IndicadorAutomaticoService): Recalcula e publica o IAPP automaticamente após o processamento de arquivos de IMOVEL, APP ou VEGETACAO.Indicadores Vigentes do Imóvel (ImovelIndicadoresService): Inclui o IAPP vigente na resposta consolidada de indicadores do imóvel (/api/imoveis/{car} e mapa).Prévia de Versão (IndicadorVersaoService): Contabiliza os registros de RESULTADO_IAPP por status (CALCULADO, SEM_APP, CAMADA_INDISPONIVEL) antes da publicação.Consolidado Municipal (ConsolidadoMunicipioService): Agrega RESULTADO_IAPP por município considerando conforme iap_passivo_ha = 0 (status = 'CALCULADO').Exportação (ConsultaExportacaoService): Inclui IAPP na contagem e na exportação de resultados da versão publicada.4. Massa de Teste e Gabarito Calculado à Mão (US#23.03 e US#23.11)Script versionado: src/main/resources/db/test/seed_teste_iapp.sqlSuíte de testes automatizados: IappConsultaServiceTest, IappControllerTest, IappPipelineEAutomaticoTest e SecurityMatrizPermissoesTest.Na latitude $\varphi \approx -25{,}19^\circ$ (SIRGAS 2000), cada quadrado base de $0{,}02^\circ \times 0{,}02^\circ$ possui área $A_{\text{imóvel}} \approx 446{,}60\text{ ha}$ (cada quadrante $0{,}01^\circ \times 0{,}01^\circ \approx 111{,}65\text{ ha}$).Código CARCenáriostatusapp_total_haapp_vegetada_hapassivo_haiapp_pctConforme (passivo_ha = 0)PR-TESTE-IAPP-01APP totalmente vegetada (metade sul do imóvel 100% coberta)CALCULADO$\frac{1}{2} A_{\text{imóvel}}$ ($\approx 223{,}30$)$\frac{1}{2} A_{\text{imóvel}}$ ($\approx 223{,}30$)0.0000100.0000SimPR-TESTE-IAPP-02APP parcialmente vegetada (4 quadrantes de APP, 3 de vegetação)CALCULADO$A_{\text{imóvel}}$ ($\approx 446{,}60$)$\frac{3}{4} A_{\text{imóvel}}$ ($\approx 334{,}95$)$\frac{1}{4} A_{\text{imóvel}}$ ($\approx 111{,}65$)75.0000NãoPR-TESTE-IAPP-03APP sem vegetação nativa (passivo integral)CALCULADO$\frac{1}{2} A_{\text{imóvel}}$ ($\approx 223{,}30$)0.0000$\frac{1}{2} A_{\text{imóvel}}$ ($\approx 223{,}30$)0.0000NãoPR-TESTE-IAPP-04Imóvel sem APP incidenteSEM_APPNULLNULLNULLNULLN/APR-TESTE-IAPP-05APPs sobrepostas (A ∪ B) + trecho fora do imóvel recortado ($3/4$ do imóvel; $2/4$ vegetados)CALCULADO$\frac{3}{4} A_{\text{imóvel}}$ ($\approx 334{,}95$)$\frac{2}{4} A_{\text{imóvel}}$ ($\approx 223{,}30$)$\frac{1}{4} A_{\text{imóvel}}$ ($\approx 111{,}65$)66.6667Não