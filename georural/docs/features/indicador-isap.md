# Indicador ISAP — Índice de Sobreposição a Áreas Protegidas (US#24)

## 1. Visão Geral

O **ISAP (Índice de Sobreposição a Áreas Protegidas)** apura, para cada imóvel rural cadastrado no GeoRural DataHub, a existência, a área em hectares e o percentual de sobreposição territorial com quatro categorias de áreas protegidas e territórios especiais, além de um consolidado total do imóvel (`TOTAL`).

---

## 2. Memória de Cálculo

### 2.1. Categorias e Fontes Oficiais

| Categoria (`isr_categoria` / `isd_categoria`) | Descrição | Fonte de Referência |
| :--- | :--- | :--- |
| `UC` | Unidades de Conservação Federais, Estaduais e Municipais | ICMBio / MMA (CNUC) |
| `TERRA_INDIGENA` | Terras Indígenas (regularizadas, homologadas, declaradas, delimitadas) | FUNAI |
| `QUILOMBOLA` | Territórios Quilombolas titulados e delimitados | INCRA |
| `FLORESTA_PUBLICA` | Florestas Públicas Federais e Estaduais cadastradas | SFB (CNFP) |
| `TOTAL` | Consolidado geométrico do imóvel considerando todas as categorias | União das 4 camadas |

### 2.2. Fórmulas, SRID e Recorte Espacial

* **Sistema de Referência Espacial (SRID):** `4674` (SIRGAS 2000), padronizado na etapa de tratamento e mantido nos metadados espaciais (`USER_SDO_GEOM_METADATA`). O cálculo de área geodésica em hectares é executado via `SDO_GEOM.SDO_AREA(..., 0.05, 'unit=HECTARE')` (encapsulado em `PKG_GEO.area_intersecao_ha`), garantindo precisão planar equivalente sem distorção angular.
* **Recorte pelo perímetro do imóvel:** Toda interseção é recortada estritamente pelo polígono válido do imóvel rural (`IMOVEL_RURAL.imo_geometria`) via `SDO_GEOM.SDO_INTERSECTION`.
* **Fórmulas do Resumo (`RESULTADO_ISAP_RESUMO`):**
  $$\text{area\_sobreposta\_ha}_{c} = \text{Área}\left(\text{Imóvel} \cap \text{CamadaDissolvida}_{c}\right)$$
  $$\text{pct\_sobreposicao}_{c} = \min\left(\frac{\text{area\_sobreposta\_ha}_{c}}{\text{area\_imovel\_ha}} \times 100,\; 100\right)$$
* **Fórmulas do Detalhe (`RESULTADO_ISAP_DETALHE`):**
  $$\text{area\_ha}_{f} = \text{Área}\left(\text{Imóvel} \cap \text{FeiçãoOriginal}_{f}\right)$$
  $$\text{pct\_sobreposicao}_{f} = \min\left(\frac{\text{area\_ha}_{f}}{\text{area\_imovel\_ha}} \times 100,\; 100\right)$$

### 2.3. Dupla Contagem e União vs. Feição Isolada

Áreas protegidas frequentemente se sobrepõem no território (por exemplo, uma Terra Indígena dentro de uma Unidade de Conservação de Uso Sustentável, ou duas UCs sobrepostas). Para evitar superestimativa dos percentuais e manter transparência analítica, o cálculo opera em **dois níveis distintos**:

1. **Nível de Resumo (`RESULTADO_ISAP_RESUMO` — União/Dissolução):**
   * **Intra-categoria:** Antes do cruzamento, as feições válidas de cada categoria são dissolvidas em uma única multigeometria na tabela `AREA_PROTEGIDA_DISSOLVIDA` (`SDO_AGGR_UNION`). Assim, duas feições sobrepostas da mesma categoria não são somadas duas vezes.
   * **Inter-categorias (`TOTAL`):** A linha `TOTAL` é calculada pela interseção do imóvel com a **união geométrica das quatro camadas dissolvidas**, nunca pela soma aritmética das linhas das categorias. Por isso:
     $$\text{area\_sobreposta\_ha}_{\text{TOTAL}} \le \sum_{c \in \{\text{UC, TI, QUI, FP}\}} \text{area\_sobreposta\_ha}_{c} \quad \text{e} \quad \text{area\_sobreposta\_ha}_{\text{TOTAL}} \le \text{area\_imovel\_ha}$$
2. **Nível de Detalhe (`RESULTADO_ISAP_DETALHE` — Feição Isolada):**
   * Cada feição original que intersecta o imóvel (`SDO_RELATE(..., 'mask=ANYINTERACT') = 'TRUE'`) tem sua interseção calculada individualmente, preservando `apr_id`, nome, subtipo e código oficial.
   * **Regra de interpretação:** Como duas feições podem ocupar a mesma porção do imóvel, a soma de `isd_area_ha` das feições listadas no detalhe **pode ser maior** que `isr_area_sobreposta_ha` da categoria ou do `TOTAL`. A API REST retorna o campo `avisoDuplaContagem` explicitando essa característica.

### 2.4. Filtro Configurável de Unidades de Conservação (`tipos_uc_incluidos`)

* A regra vigente do ISAP (`REGRA_CALCULO` com `ind_sigla = 'ISAP'`) possui o parâmetro configurável `tipos_uc_incluidos` na tabela `PARAMETRO_REGRA_CALCULO` (valor padrão `'*'`, significando todas as UCs).
* Quando configurado com uma lista delimitada (ex.: `'PARNA,ESEC,REBIO'` para Proteção Integral), somente as feições de `AREA_PROTEGIDA` com `apr_tipo = 'UC'` cujo `apr_subtipo` corresponda ao filtro participam da dissolução (`AREA_PROTEGIDA_DISSOLVIDA.apd_filtro_aplicado`) e do detalhamento.
* Todas as UCs filtradas são consolidadas conjuntamente na única categoria `UC`, e o filtro efetivamente aplicado fica gravado em `RESULTADO_ISAP_RESUMO.isr_filtro_aplicado` para auditoria e reprodutibilidade.

### 2.5. Tratamento de Ausência de Sobreposição e Camada Indisponível

| Status (`isr_status`) | Condição | `isr_sobrepoe` | `isr_area_sobreposta_ha` | `isr_pct_sobreposicao` | Comportamento no `TOTAL` |
| :--- | :--- | :---: | :---: | :---: | :--- |
| `COM_SOBREPOSICAO` | Camada publicada e interseção $> 0\text{ ha}$ | `'S'` | $> 0$ | $> 0$ e $\le 100$ | `'COM_SOBREPOSICAO'` (se nenhuma categoria estiver indisponível) |
| `SEM_SOBREPOSICAO` | Camada publicada e sem interseção ($0\text{ ha}$) | `'N'` | `0.0000` | `0.0000` | `'SEM_SOBREPOSICAO'` (se todas as 4 estiverem sem sobreposição) |
| `CAMADA_INDISPONIVEL` | Camada da categoria ausente ou sem versão publicada | `NULL` | `NULL` | `NULL` | **Propaga `'CAMADA_INDISPONIVEL'`** com `NULL` para não subestimar o índice |

---

## 3. Dicionário de Dados

### 3.1. `RESULTADO_ISAP_RESUMO`

Armazena o resultado consolidado por imóvel, versão e categoria (4 categorias + `TOTAL`), incluindo rastreabilidade completa da camada e da regra aplicada.

| Coluna | Tipo Oracle | Nulo | Padrão | Descrição / Restrições |
| :--- | :--- | :---: | :---: | :--- |
| `isr_id` | `NUMBER` | Não | `IDENTITY` | Chave primária (`pk_resultado_isap_resumo`). |
| `imo_id` | `NUMBER` | Não | — | FK para `IMOVEL_RURAL(imo_id)` (`fk_isr_imovel`). |
| `ver_id` | `NUMBER` | Não | — | FK para `VERSAO(ver_id)` da rodada de cálculo (`fk_isr_versao`). |
| `isr_camada_ver_id` | `NUMBER` | Sim | — | FK para `VERSAO(ver_id)` da camada de áreas protegidas utilizada na categoria (`fk_isr_camada_ver`). |
| `exe_id` | `NUMBER` | Sim | — | FK para `EXECUCAO_PIPELINE(exe_id)` que gerou o cálculo (`fk_isr_execucao`). |
| `rca_id` | `NUMBER` | Sim | — | FK para `REGRA_CALCULO(rca_id)` aplicada (`fk_isr_regra`). |
| `isr_categoria` | `VARCHAR2(20)` | Não | — | Domínio: `'UC'`, `'TERRA_INDIGENA'`, `'QUILOMBOLA'`, `'FLORESTA_PUBLICA'`, `'TOTAL'` (`ck_isr_categoria`). Única por `(imo_id, ver_id, isr_categoria)`. |
| `isr_sobrepoe` | `CHAR(1)` | Sim | — | `'S'` (há sobreposição), `'N'` (sem sobreposição) ou `NULL` (camada indisponível). |
| `isr_area_sobreposta_ha` | `NUMBER(14,4)` | Sim | — | Área sobreposta dissolvida em hectares (`>= 0`, ou `NULL` se indisponível). |
| `isr_pct_sobreposicao` | `NUMBER(7,4)` | Sim | — | Percentual sobre a área do imóvel (`0` a `100`, ou `NULL` se indisponível). |
| `isr_area_imovel_ha` | `NUMBER(14,4)` | Não | — | Área total do imóvel em hectares usada como denominador (`>= 0`). |
| `isr_filtro_aplicado` | `VARCHAR2(500)` | Sim | — | Filtro de subtipos de UC aplicado na categoria `UC` (ex.: `'*'` ou `'PARNA,ESEC'`). |
| `isr_competencia_camada` | `VARCHAR2(20)` | Sim | — | Competência (ex.: `'2026-11'`) da camada utilizada na categoria. |
| `isr_status` | `VARCHAR2(25)` | Não | — | `'COM_SOBREPOSICAO'`, `'SEM_SOBREPOSICAO'` ou `'CAMADA_INDISPONIVEL'` (`ck_isr_coerencia_status`). |
| `isr_vigente` | `CHAR(1)` | Não | `'N'` | `'S'` se pertence à versão publicada vigente; `'N'` caso contrário. |
| `isr_data_calculo` | `TIMESTAMP` | Não | `SYSTIMESTAMP` | Data e hora da execução do cálculo. |

### 3.2. `RESULTADO_ISAP_DETALHE`

Armazena o detalhamento por feição individual atingida dentro de cada categoria (não gera linhas para `TOTAL`).

| Coluna | Tipo Oracle | Nulo | Padrão | Descrição / Restrições |
| :--- | :--- | :---: | :---: | :--- |
| `isd_id` | `NUMBER` | Não | `IDENTITY` | Chave primária (`pk_resultado_isap_detalhe`). |
| `isr_id` | `NUMBER` | Não | — | FK para `RESULTADO_ISAP_RESUMO(isr_id)` com `ON DELETE CASCADE` (`fk_isd_resumo`). |
| `imo_id` | `NUMBER` | Não | — | FK para `IMOVEL_RURAL(imo_id)` (`fk_isd_imovel`). |
| `ver_id` | `NUMBER` | Não | — | FK para `VERSAO(ver_id)` (`fk_isd_versao`). |
| `apr_id` | `NUMBER` | Sim | — | FK para `AREA_PROTEGIDA(apr_id)` com `ON DELETE SET NULL` (`fk_isd_area_pro`). |
| `isd_categoria` | `VARCHAR2(20)` | Não | — | Domínio: `'UC'`, `'TERRA_INDIGENA'`, `'QUILOMBOLA'`, `'FLORESTA_PUBLICA'` (`ck_isd_categoria`). |
| `isd_nome_feicao` | `VARCHAR2(255)` | Sim | — | Nome oficial da área protegida atingida (ex.: `'PARNA Campos Gerais'`). |
| `isd_tipo_feicao` | `VARCHAR2(100)` | Sim | — | Subtipo/grupo da feição (ex.: `'PARNA'`, `'Homologada'`, `'Titulada'`). |
| `isd_codigo_feicao` | `VARCHAR2(80)` | Sim | — | Código identificador na fonte oficial (CNUC, FUNAI, INCRA, CNFP). |
| `isd_area_ha` | `NUMBER(14,4)` | Não | — | Área da interseção do imóvel com esta feição isolada em hectares (`>= 0`). |
| `isd_pct_sobreposicao` | `NUMBER(7,4)` | Não | — | Percentual da feição isolada em relação à área do imóvel (`0` a `100`). |

### 3.3. Tabela Auxiliar `AREA_PROTEGIDA_DISSOLVIDA`

Cache espacial da união (`SDO_AGGR_UNION`) por `(ver_id, apd_categoria, apd_filtro_aplicado)` para evitar recomputação da dissolução a cada imóvel e eliminar dupla contagem intra-categoria.

---

## 4. Contrato REST e Controle de Acesso (OpenAPI / Swagger)

* **Base path:** `/api/v1/isap` (documentado no Swagger UI em `/swagger-ui.html` e `/v3/api-docs` sob a tag `ISAP - Áreas Protegidas`).
* **Perfis autorizados:** `ADMIN`, `GESTOR`, `OPERADOR`, `ANALISTA` e `AUDITOR` (requisições sem token JWT válido retornam `401 Unauthorized`).
* **Endpoints:**
  * `GET /api/v1/isap` — Lista paginada (`PaginaResponse<IsapListaItemResponse>`) da linha `TOTAL` vigente de cada imóvel, com filtros opcionais `status`, `municipioId` e `busca` (código CAR ou nome do imóvel).
  * `GET /api/v1/isap/imoveis/{imovelId}?versaoId=` — Detalhamento completo (`IsapImovelConsultaResponse`) contendo metadados do imóvel, `avisoDuplaContagem`, array `resumo` (4 categorias + `TOTAL`) e array `detalhes` (feições atingidas).
  * `GET /api/v1/isap/imoveis/car/{codigoCar}?versaoId=` — Mesmo contrato acima consultado pelo código CAR.