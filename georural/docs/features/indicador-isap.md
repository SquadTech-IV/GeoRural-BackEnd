# Especificação Funcional e Técnica — Indicador ISAP (US#24)

**Indicador:** Índice de Sobreposição a Áreas Protegidas (`ISAP`)  
**Objetivo:** Avaliar se e quanto cada imóvel rural (`IMOVEL_RURAL`) se sobrepõe a áreas legalmente protegidas, detalhando o consolidado por categoria, o total sem dupla contagem e cada feição atingida.

---

## 1. Regras de Cálculo do ISAP (US#24.01)

### 1.1 Categorias e Fontes Oficiais
O ISAP avalia quatro categorias distintas de áreas protegidas, armazenadas na tabela `AREA_PROTEGIDA` (`apr_tipo`):

| Código Categoria (`apr_tipo`) | Descrição | Fonte Oficial |
| :--- | :--- | :--- |
| `UC` | Unidades de Conservação (Proteção Integral e Uso Sustentável) | ICMBio / MMA (CNUC) |
| `TERRA_INDIGENA` | Terras Indígenas (regularizadas, homologadas, declaradas, delimitadas) | FUNAI |
| `QUILOMBOLA` | Territórios Quilombolas (titulados e delimitados) | INCRA |
| `FLORESTA_PUBLICA` | Florestas Públicas (Tipo A e Tipo B — CNFP) | SFB (Serviço Florestal Brasileiro) |

### 1.2 Sistema de Referência Espacial (SRID) e Recorte
* **SRID:** Utiliza o mesmo SRID consolidado do projeto nas tabelas espaciais (`IMOVEL_RURAL`, `LIMITE_TERRITORIAL`, `AREA_PROTEGIDA`), com reprojeção garantida via `SDO_CS.TRANSFORM` quando a camada de origem divergir do SRID alvo, e tolerância geodésica/planar padrão `c_tol = 0.05` (`PKG_GEO.c_tol`).
* **Recorte Territorial (UF):** Durante o tratamento da camada (`PKG_TRATAMENTO`), as feições que cruzam a fronteira do Estado são recortadas pelo limite oficial da UF (`LIMITE_TERRITORIAL` onde `lim_tipo = 'UF'`) e feições totalmente externas são descartadas para quarentena.
* **Recorte pelo Perímetro do Imóvel:** No cálculo do indicador, toda medição ocorre estritamente sobre a interseção geométrica (`SDO_GEOM.SDO_INTERSECTION`) recortada pelo polígono do imóvel (`IMOVEL_RURAL.imo_geometria`).

### 1.3 Tratamento de Dupla Contagem (União Dissolvida vs. Feição Isolada)
Para evitar superestimativa de área sobreposta:
1. **Resumo por Categoria (Sem dupla contagem intra-categoria):**
   * Todas as feições válidas da categoria (já filtradas, no caso de `UC`) são dissolvidas em uma única geometria consolidada via `SDO_AGGR_UNION` (por categoria e versão).
   * A área sobreposta da categoria é obtida pela interseção do perímetro do imóvel com essa **geometria dissolvida**:
     $$\text{Área Sobreposta}_{\text{cat}}\text{ (ha)} = \text{SDO\_AREA}\Big(\text{SDO\_INTERSECTION}(\text{Imóvel}, \text{CamadaDissolvida}_{\text{cat}}),\ \text{'unit=HECTARE'}\Big)$$
2. **Resumo `TOTAL` (Sem dupla contagem inter-categorias):**
   * Quando uma Terra Indígena sobrepõe uma Unidade de Conservação dentro do mesmo imóvel, a área comum **não pode ser somada duas vezes**.
   * O `TOTAL` é calculado sobre a **união geométrica das 4 camadas dissolvidas** (`SDO_AGGR_UNION` das 4 categorias) interseccionada com o imóvel:
     $$\text{Área Sobreposta}_{\text{TOTAL}}\text{ (ha)} = \text{SDO\_AREA}\Big(\text{SDO\_INTERSECTION}\Big(\text{Imóvel}, \bigcup_{c=1}^{4} \text{CamadaDissolvida}_{c}\Big),\ \text{'unit=HECTARE'}\Big)$$
   * **Invariantes matemáticas garantidas:**
     * $\text{Área Sobreposta}_{\text{TOTAL}} \le \sum_{c=1}^{4} \text{Área Sobreposta}_{c}$
     * $\text{Área Sobreposta}_{\text{TOTAL}} \le \text{Área Imóvel}$
     * $\text{pct\_sobreposicao} \in [0,\ 100]$ (com `LEAST(..., 100)` para mitigar arredondamentos de tolerância espacial).
3. **Detalhe por Feição (Feição Isolada):**
   * O detalhamento lista **cada feição individual** da tabela `AREA_PROTEGIDA` que intersecciona o imóvel (`SDO_RELATE(..., 'mask=ANYINTERACT') = 'TRUE'`).
   * A área e o percentual de cada linha de detalhe são calculados sobre a **feição isolada** recortada pelo imóvel.
   * **Regra de exibição:** Como duas feições podem se sobrepor entre si dentro da mesma categoria ou entre categorias distintas, **a soma das áreas das feições no detalhe pode ser maior que `area_sobreposta_ha` do resumo**.

### 1.4 Fórmula Percentual
Para cada categoria e para o `TOTAL`:
$$\text{pct\_sobreposicao} = \min\left(100,\ \frac{\text{area\_sobreposta\_ha}}{\text{area\_imovel\_ha}} \times 100\right)$$
*(Caso `area_imovel_ha <= 0` ou nulo, `pct_sobreposicao = 0`.)*

### 1.5 Filtro Parametrizável por Tipo de Unidade de Conservação (`UC`)
* O filtro de tipos de UC incluídos no cálculo é parametrizado na regra de cálculo vigente do indicador `ISAP` (`PARAMETRO_REGRA_CALCULO`, parâmetro `tipos_uc_incluidos`, ex.: lista separada por vírgula de grupos/categorias do SNUC como `PROTECAO_INTEGRAL,USO_SUSTENTAVEL` ou categorias específicas como `PARNA,ESEC,REBIO,MONA,REVIS,APA,ARIE,FLONA,RESEX,RDS,RPPN`, ou `*` para todas).
* Todos os tipos incluídos pelo filtro são dissolvidos **em conjunto** na única categoria `UC`.
* Feições de `UC` cujo subtipo/categoria não esteja na lista do filtro são desconsideradas tanto no resumo quanto no detalhe.
* O valor efetivo do filtro aplicado na execução é gravado na coluna `filtro_aplicado` do resultado da categoria `UC` (e refletido na rastreabilidade) para transparência ao analista.

### 1.6 Tratamento de Ausência de Sobreposição e Camada Indisponível
| Situação da Camada / Interseção | `status` | `sobrepoe` | `area_sobreposta_ha` | `pct_sobreposicao` |
| :--- | :--- | :--- | :--- | :--- |
| Camada publicada e há interseção (`area > 0`) | `COM_SOBREPOSICAO` | `true` (`S`) | Valor calculado (`> 0`) | Valor calculado (`> 0`) |
| Camada publicada e não há interseção (`area = 0`) | `SEM_SOBREPOSICAO` | `false` (`N`) | `0.0000` | `0.0000` |
| Camada não publicada / sem versão vigente | `CAMADA_INDISPONIVEL` | `null` | `NULL` | `NULL` |

* **Regra crítica para o `TOTAL`:** Se **qualquer uma** das 4 categorias estiver com status `CAMADA_INDISPONIVEL`, a linha `TOTAL` **deve** receber `status = 'CAMADA_INDISPONIVEL'`, `sobrepoe = null`, `area_sobreposta_ha = NULL` e `pct_sobreposicao = NULL`, evitando subestimar o passivo ambiental do imóvel. As categorias que estiverem disponíveis continuam exibindo seus valores individuais normalmente.

---

## 2. Contrato de Saída do ISAP (US#24.02)

A saída do indicador ISAP é estruturada em **dois níveis** (tanto na persistência relacional quanto no contrato JSON da API REST):

### 2.1 Nível 1 — Resumo por Categoria e TOTAL (`resumo`)
Contém **obrigatoriamente 5 registros por imóvel** (1 para cada categoria: `UC`, `TERRA_INDIGENA`, `QUILOMBOLA`, `FLORESTA_PUBLICA` + 1 consolidado `TOTAL`).

| Campo JSON | Coluna Oracle | Tipo | Nulável | Descrição |
| :--- | :--- | :--- | :--- | :--- |
| `categoria` | `isr_categoria` | `String` (Enum) | Não | `UC`, `TERRA_INDIGENA`, `QUILOMBOLA`, `FLORESTA_PUBLICA` ou `TOTAL` |
| `sobrepoe` | `isr_sobrepoe` | `Boolean` (`CHAR(1)` `S`/`N`) | Sim | `true` se `COM_SOBREPOSICAO`; `false` se `SEM_SOBREPOSICAO`; `null` se `CAMADA_INDISPONIVEL` |
| `areaSobrepostaHa` | `isr_area_sobreposta_ha` | `BigDecimal` (`NUMBER(14,4)`) | Sim | Área sobreposta em hectares (`>= 0`). `0` se sem sobreposição; `null` se camada indisponível |
| `pctSobreposicao` | `isr_pct_sobreposicao` | `BigDecimal` (`NUMBER(7,4)`) | Sim | Percentual sobre a área do imóvel (`0` a `100`). `0` se sem sobreposição; `null` se indisponível |
| `areaImovelHa` | `isr_area_imovel_ha` | `BigDecimal` (`NUMBER(14,4)`) | Não | Área total do imóvel em hectares usada como denominador |
| `filtroAplicado` | `isr_filtro_aplicado` | `String` (`VARCHAR2(500)`) | Sim | Filtro de tipos de UC aplicado (preenchido em `UC` e consolidado no `TOTAL`; `null` nas demais) |
| `status` | `isr_status` | `String` (Enum) | Não | `COM_SOBREPOSICAO`, `SEM_SOBREPOSICAO` ou `CAMADA_INDISPONIVEL` |
| `rastreabilidade` | *(FKs + metadados)* | `Object` | Não | Versão e competência da camada da categoria, execução (`exeId`), regra (`rcaId` / versão) |

### 2.2 Nível 2 — Detalhe por Feição Atingida (`detalhes`)
Contém **0 ou N registros por imóvel**, representando cada feição individual de área protegida que intersecciona o imóvel (somente de camadas disponíveis e aprovadas pelo filtro de UC).

| Campo JSON | Coluna Oracle | Tipo | Nulável | Descrição |
| :--- | :--- | :--- | :--- | :--- |
| `categoria` | `isd_categoria` | `String` (Enum) | Não | `UC`, `TERRA_INDIGENA`, `QUILOMBOLA` ou `FLORESTA_PUBLICA` |
| `nomeFeicao` | `isd_nome_feicao` | `String` (`VARCHAR2(255)`) | Sim | Nome oficial da área protegida (ex.: `"Parque Nacional do Iguaçu"`) |
| `tipoFeicao` | `isd_tipo_feicao` | `String` (`VARCHAR2(100)`) | Sim | Subtipo/categoria específica (ex.: `"PARNA"`, `"APA"`, `"TI Homologada"`, `"Floresta Tipo A"`) |
| `codigoFeicao` | `isd_codigo_feicao` | `String` (`VARCHAR2(80)`) | Sim | Código identificador na fonte oficial (ex.: código CNUC, código FUNAI, código INCRA/CNFP) |
| `areaHa` | `isd_area_ha` | `BigDecimal` (`NUMBER(14,4)`) | Não | Área da interseção desta feição isolada com o imóvel, em hectares (`> 0`) |
| `pct` | `isd_pct_sobreposicao` | `BigDecimal` (`NUMBER(7,4)`) | Não | Percentual da feição isolada sobre a área total do imóvel (`0` a `100`) |

### 2.3 Exemplo de Payload JSON (Contrato REST)
```json
{
  "imovelId": 121,
  "codigoCar": "PR-4106902-1A2B3C4D5E6F7G8H9I0J",
  "nomeImovel": "Fazenda Santa Clara",
  "areaImovelHa": 500.0000,
  "dataCalculo": "2026-11-22T10:15:30",
  "vigente": true,
  "avisoDuplaContagem": "As áreas das feições individuais abaixo representam cada polígono isolado e podem se sobrepor entre si; por isso, sua soma pode exceder o total consolidado da categoria ou o TOTAL geral.",
  "resumo": [
    {
      "categoria": "UC",
      "sobrepoe": true,
      "areaSobrepostaHa": 75.0000,
      "pctSobreposicao": 15.0000,
      "areaImovelHa": 500.0000,
      "filtroAplicado": "PROTECAO_INTEGRAL,USO_SUSTENTAVEL",
      "status": "COM_SOBREPOSICAO",
      "rastreabilidade": {
        "versaoId": 14,
        "versaoNumero": 2,
        "competencia": "2026-10",
        "execucaoId": 88,
        "regraVersao": "v1"
      }
    },
    {
      "categoria": "TERRA_INDIGENA",
      "sobrepoe": true,
      "areaSobrepostaHa": 50.0000,
      "pctSobreposicao": 10.0000,
      "areaImovelHa": 500.0000,
      "filtroAplicado": null,
      "status": "COM_SOBREPOSICAO",
      "rastreabilidade": {
        "versaoId": 15,
        "versaoNumero": 1,
        "competencia": "2026-09",
        "execucaoId": 88,
        "regraVersao": "v1"
      }
    },
    {
      "categoria": "QUILOMBOLA",
      "sobrepoe": false,
      "areaSobrepostaHa": 0.0000,
      "pctSobreposicao": 0.0000,
      "areaImovelHa": 500.0000,
      "filtroAplicado": null,
      "status": "SEM_SOBREPOSICAO",
      "rastreabilidade": {
        "versaoId": 16,
        "versaoNumero": 1,
        "competencia": "2026-08",
        "execucaoId": 88,
        "regraVersao": "v1"
      }
    },
    {
      "categoria": "FLORESTA_PUBLICA",
      "sobrepoe": false,
      "areaSobrepostaHa": 0.0000,
      "pctSobreposicao": 0.0000,
      "areaImovelHa": 500.0000,
      "filtroAplicado": null,
      "status": "SEM_SOBREPOSICAO",
      "rastreabilidade": {
        "versaoId": 17,
        "versaoNumero": 1,
        "competencia": "2026-08",
        "execucaoId": 88,
        "regraVersao": "v1"
      }
    },
    {
      "categoria": "TOTAL",
      "sobrepoe": true,
      "areaSobrepostaHa": 100.0000,
      "pctSobreposicao": 20.0000,
      "areaImovelHa": 500.0000,
      "filtroAplicado": "PROTECAO_INTEGRAL,USO_SUSTENTAVEL",
      "status": "COM_SOBREPOSICAO",
      "rastreabilidade": {
        "versaoId": 10,
        "versaoNumero": 3,
        "competencia": "2026-11",
        "execucaoId": 88,
        "regraVersao": "v1"
      }
    }
  ],
  "detalhes": [
    {
      "categoria": "UC",
      "nomeFeicao": "Parque Nacional do Iguaçu",
      "tipoFeicao": "PARNA",
      "codigoFeicao": "0000.00.0123",
      "areaHa": 75.0000,
      "pct": 15.0000
    },
    {
      "categoria": "TERRA_INDIGENA",
      "nomeFeicao": "TI Rio das Cobras",
      "tipoFeicao": "HOMOLOGADA",
      "codigoFeicao": "TI-41001",
      "areaHa": 50.0000,
      "pct": 10.0000
    }
  ]
}