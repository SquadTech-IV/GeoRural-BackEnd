# Convenção de Caminho e Metadados dos Objetos na Zona Bruta

**User Story**: US#07 - Recebimento e Registro Imutável de Arquivos de Dados  
**Bucket**: `georural-bruta` (OCI Object Storage)  
**Classe de Implementação**: `br.com.fatec.georural.service.storage.CaminhoZonaBrutaHelper`

---

## 1. Visão Geral

Todo arquivo de dados (Shapefile em `.zip`, GeoJSON, GeoPackage, CSV e GeoTIFF) recebido pelo backend é depositado de forma imutável (*Write-Once*) no bucket `georural-bruta`. Para assegurar organização, rastreabilidade determinística, isolamento por conjunto de dados e prevenção de colisões, foi estabelecida uma convenção padronizada para o caminho (*object key*) e para os metadados associados ao objeto no OCI Object Storage.

---

## 2. Estrutura do Caminho (*Object Key*)

O caminho relativo do objeto dentro do bucket `georural-bruta` segue o seguinte formato:

```text
conjuntos/{conId}/brutos/{ano}/{mes}/{hash}_{nomeOriginal}
```

### Detalhamento dos Componentes

| Segmento | Formato / Tipo | Descrição | Exemplo |
|---|---|---|---|
| `conjuntos` | Literal fixo | Prefixo raiz para particionamento dos recursos por conjunto de dados. | `conjuntos` |
| `{conId}` | Numérico (`Long`) | Identificador único do conjunto de dados (`CONJUNTO_DADOS.con_id`). | `10` |
| `brutos` | Literal fixo | Subdiretório que identifica explicitamente a zona bruta de dados. | `brutos` |
| `{ano}` | `YYYY` (4 dígitos) | Ano do recebimento do arquivo pelo sistema. | `2026` |
| `{mes}` | `MM` (2 dígitos) | Mês do recebimento com preenchimento de zero à esquerda (`01` a `12`). | `10` |
| `{hash}` | String hex (64 caracteres) | Hash criptográfico SHA-256 do arquivo em formato hexadecimal minúsculo. | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `{nomeOriginal}` | String sanitizada | Nome original do arquivo recebido, com caracteres de caminho removidos (sem path traversal). | `limites_imoveis_sp.zip` |

---

## 3. Exemplos de Caminhos Válidos

### Exemplo 1: Shapefile (.zip)
- **Conjunto (`conId`)**: `10`
- **Data de Recebimento**: `2026-10-18T19:30:00`
- **Hash SHA-256**: `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- **Arquivo Original**: `limites_imoveis_sp.zip`
- **Caminho**:
  ```text
  conjuntos/10/brutos/2026/10/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855_limites_imoveis_sp.zip
  ```

### Exemplo 2: GeoJSON
- **Conjunto (`conId`)**: `42`
- **Data de Recebimento**: `2026-04-05T10:15:30`
- **Hash SHA-256**: `8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4`
- **Arquivo Original**: `embargos_ibama.geojson`
- **Caminho**:
  ```text
  conjuntos/42/brutos/2026/04/8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4_embargos_ibama.geojson
  ```

---

## 4. Metadados do Objeto no Object Storage (OCI `opcMeta`)

Conforme os requisitos FR-019 e FR-020 da especificação, todo objeto gravado na zona bruta recebe metadados personalizados armazenados diretamente no cabeçalho do OCI Object Storage (`opc-meta-*`):

| Chave de Metadado | Cabeçalho HTTP OCI | Descrição | Exemplo |
|---|---|---|---|
| `hash-sha256` | `opc-meta-hash-sha256` | Hash SHA-256 do arquivo original calculado em fluxo. | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `con-id` | `opc-meta-con-id` | Identificador do conjunto de dados de destino em `CONJUNTO_DADOS`. | `10` |
| `usu-id` | `opc-meta-usu-id` | Identificador do usuário responsável provisório em `USUARIO`. | `1` |

---

## 5. Sanitização de Nome e Segurança

Para evitar ataques de *Path Traversal* (injeção de `../` ou separadores de diretório absolutos como `C:\` ou `/etc/`), o nome do arquivo passa pelo método `CaminhoZonaBrutaHelper.extrairNomeArquivo(nomeOriginal)`, que remove quaisquer caminhos precedentes e retém estritamente o nome base do arquivo.

---

## 6. Correspondência no Catálogo Relacional

O caminho gerado é persistido na coluna `ARQUIVO_BRUTO.arq_path_armazenamento` (VARCHAR2(500)), permitindo correlação direta entre o banco de dados Oracle e o OCI Object Storage.
