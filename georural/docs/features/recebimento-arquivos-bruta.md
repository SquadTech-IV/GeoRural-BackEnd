# US#07 — Recebimento e registro imutável de arquivos na zona bruta

Este documento descreve o que foi implementado na US#07. Tudo o que não pôde ser verificado está marcado
como **a confirmar** (ver seção 8).

## 1. Visão geral

Todo arquivo recebido por upload é guardado exatamente como chegou no bucket `georural-bruta`, com o
hash SHA-256 do conteúdo e o registro de quem enviou, quando e para qual conjunto de dados, em
`ARQUIVO_BRUTO`. Recalcular o hash do objeto armazenado dá o mesmo valor registrado.

Fora de escopo desta feature: tela do portal (T012), listagem e download de arquivos brutos,
autenticação (US#13) e o processamento posterior dos arquivos.

### Fluxo de recebimento

1. Validações baratas: conjunto, extensão e responsável informados e existentes.
2. O conteúdo é copiado para um arquivo temporário em disco (`java.io.tmpdir`, prefixo
   `georural-recebimento-`), calculando o SHA-256 durante a cópia. O hash é recalculado a partir do arquivo
   em disco e os dois valores devem coincidir.
3. Validação do conteúdo (assinatura dos primeiros bytes).
4. Verificação de duplicidade pelo hash.
5. Registro em `ARQUIVO_BRUTO` com situação `RECEBENDO`, confirmado no banco antes da gravação no bucket
   (reserva o hash).
6. Gravação na zona bruta com `If-None-Match: *` (nunca sobrescreve).
7. Situação atualizada para `RECEBIDO`. O arquivo temporário é sempre apagado.

O serviço não é transacional de propósito: o registro `RECEBENDO` precisa estar confirmado no banco antes da gravação.

## 2. Dicionário de dados — `ARQUIVO_BRUTO`

Script de alteração: `src/main/resources/db/V4__schema_update.sql` e `src/main/resources/db/V5_dados_usuarios.sql`(aplicado manualmente; o
projeto não usa ferramenta de migração).

| Coluna | Tipo | Nulo | Descrição |
|---|---|---|---|
| `arq_bruto_id` | NUMBER (identity) | não | Identificador do registro. |
| `con_id` | NUMBER | não | Conjunto de destino (FK `fk_arq_conjunto` → `CONJUNTO_DADOS`). |
| `usu_id` | NUMBER | **sim** | Responsável pelo envio (FK `fk_arq_usuario` → `USUARIO`). |
| `arq_nome` | VARCHAR2(255) | não | Nome original do arquivo (somente o nome, sem caminho). |
| `arq_formato` | VARCHAR2(15) | sim | `SHP`, `GEOJSON`, `GPKG`, `CSV` ou `TIF`. |
| `arq_path_armazenamento` | VARCHAR2(500) | não | Caminho do objeto no bucket (ver seção 3). |
| `arq_tamanho_bytes` | NUMBER(15) | sim | Tamanho em bytes. |
| `arq_status` | VARCHAR2(15) | sim | Situação do registro (ver abaixo). |
| `arq_hash` | CHAR(64) | não | SHA-256 em hexadecimal minúsculo. |
| `arq_algoritmo_hash` | VARCHAR2(10) | não | Padrão `SHA-256`. Acrescentada pela V4. |
| `arq_canal_entrada` | VARCHAR2(10) | não | `UPLOAD` ou `API` (`ck_arq_canal`). Padrão `UPLOAD`. Acrescentada pela V4. |
| `arq_data_upload` | TIMESTAMP | não | Data e hora do recebimento. |

Constraints: `pk_arquivo_bruto`, `uk_arquivo_hash` (**hash único globalmente**, ver seção 6),
`fk_arq_conjunto`, `fk_arq_usuario`, `ck_arq_status`, `ck_arq_canal`. Existe também o índice não único
`idx_arquivo_conjunto` (`colunas: con_id`).

Situações (`ck_arq_status`):

| Situação | Uso |
|---|---|
| `RECEBENDO` | Vaga reservada; o objeto pode ainda não existir no bucket. |
| `RECEBIDO` | Objeto gravado e registro concluído. |
| `FALHA` | Permitida pela constraint, mas **não usada**: com hash único global, um registro `FALHA` prenderia o hash. O registro é apagado quando se sabe que o objeto não foi gravado. |
| `VALIDANDO`, `APROVADO`, `REJEITADO`, `PROCESSADO` | Pertencem a fluxos posteriores; esta feature não as usa. |

`usu_id` aceita nulo no banco porque o fluxo de importação de embargos cria `ARQUIVO_BRUTO` sem usuário. Nos
envios por upload o responsável é obrigatório, garantido pelo serviço de recebimento.

## 3. Convenção de caminho

```text
conjuntos/{conId}/brutos/{ano}/{mes}/{hash}_{nomeOriginal}
```

Detalhes, exemplos e metadados do objeto em `docs/conventions/caminho-zona-bruta.md`.

Regra do fluxo: o caminho é calculado uma única vez, na criação do registro `RECEBENDO` (com a data do
envio), e gravado em `arq_path_armazenamento`. Em uma retomada o caminho gravado é reaproveitado, nunca
recalculado, para que a mudança de mês não crie um segundo objeto.

## 4. Política de imutabilidade e permissões

**No código**
- Toda gravação na zona bruta usa `If-None-Match: *`; se o objeto já existe, a OCI responde HTTP 412 e o
  objeto não é alterado.
- As classes de acesso à zona bruta (`PoliticaZonaBruta`, `ArmazenamentoZonaBrutaService`) não têm método
  de apagar nem de alterar objetos. Testes unitários verificam isso.
- O backend só apaga o arquivo temporário local e o registro `RECEBENDO` quando é certo que o objeto não
  foi gravado.

**Na infraestrutura**
- Tipo da regra de retenção do bucket `georural-bruta` é editável.
- O usuário da aplicação (perfil `GEORURAL_APP`) **não** tem permissão de apagar ou sobrescrever objetos na zona.

O `If-None-Match` protege contra sobrescrita pela aplicação; mas a imutabilidade também é garantida pela nuvem.

## 5. Endpoint

`POST /api/arquivos/brutos/upload` — `multipart/form-data`

| Campo | Obrigatório | Descrição |
|---|---|---|
| `arquivo` | sim | Arquivo a armazenar (limite configurado de 300 MB). |
| `conId` | sim | Conjunto de dados de destino. |
| `usuId` | sim | Responsável pelo envio. Identificação provisória: verifica que o usuário existe, mas **não autentica** o chamador (até a US#13). |

| Resposta | Quando |
|---|---|
| `201` | Arquivo recebido e registrado. Corpo: id, conjunto, nome, formato, tamanho, hash, algoritmo, canal, situação e data/hora. Não expõe o caminho do objeto. |
| `400` | Campo obrigatório ausente ou inválido; extensão não aceita (inclui `.json`); conteúdo incompatível com a extensão; zip sem `.shp`, `.shx` e `.dbf`; conjunto arquivado ou que não aceita upload. |
| `404` | Conjunto ou usuário não encontrado. |
| `409` | Já existe arquivo com o mesmo hash. |
| `500` | Erro interno. A mensagem devolvida é genérica; o detalhe fica no log. |

Documentação OpenAPI gerada pelo `springdoc-openapi` (anotações no controller). Endereços esperados:
`/v3/api-docs` e `/swagger-ui/index.html`.

Exemplo:

```bash
curl -i -X POST -F "conId=1" -F "usuId=2" -F "arquivo=@dados.csv" http://localhost:8080/api/arquivos/brutos/upload
```

## 6. Decisões

**Hash.** SHA-256, calculado em fluxo (buffer de 64 KB), sem carregar o arquivo na memória. Resultado em hexadecimal minúsculo com 64 caracteres. O cálculo é conferido duas vezes (conteúdo recebido e arquivo em disco). O pipeline de importação de embargos tem o próprio método privado de cálculo, com o mesmo resultado; não foi unificado para não alterar um fluxo existente.

**Duplicado.** O hash é único **globalmente** (`uk_arquivo_hash`), decisão baseada na arquitetura do banco. Isso difere do critério de aceite original da US ("mesmo hash para o mesmo conjunto"): o mesmo arquivo enviado a outro conjunto também recebe `409`.
- A verificação em código rejeita o caso comum; envios simultâneos são decididos pela constraint do banco
  (violação de `UK_ARQUIVO_HASH` vira `409`).
- Retomada: um registro `RECEBENDO` do **mesmo conjunto**, mais antigo que
  `georural.recebimento.prazo-abandono-minutos` (padrão 15 minutos), é considerado abandonado. O reenvio
  conclui o registro (objeto já gravado) ou grava o objeto no caminho já registrado.
- Limitação: um `RECEBENDO` abandonado de outro conjunto continua bloqueando o hash e só é liberado
  manualmente.

**Falhas na gravação.**

| Situação | Resultado |
|---|---|
| Erro antes de gravar e o objeto não existe | Registro apagado; erro `500`. |
| Erro, mas o objeto existe | Conclui como `RECEBIDO`. |
| Resposta 412 (objeto já existe) | Conclui como `RECEBIDO`. |
| Não é possível confirmar se o objeto existe | Registro permanece `RECEBENDO` até a retomada; erro `500`. |

**Formatos aceitos** (valida extensão e assinatura dos primeiros bytes; o conteúdo geoespacial não é
interpretado):

| Extensão | `arq_formato` | Validação do conteúdo |
|---|---|---|
| `.zip` | `SHP` | Assinatura zip; deve conter `.shp`, `.shx` e `.dbf` com o mesmo nome base. |
| `.geojson` | `GEOJSON` | Primeiro caractere não branco é `{` (tolera BOM UTF-8). |
| `.gpkg` | `GPKG` | Cabeçalho `SQLite format 3`. Qualquer arquivo SQLite com essa extensão passa. |
| `.csv` | `CSV` | Sem byte nulo nos primeiros 8 KB. Não exige UTF-8. |
| `.tif`, `.tiff` | `TIF` | Assinatura TIFF ou BigTIFF. |

`.json` é rejeitado; use `.geojson`.

**Área temporária.** Disco local do servidor (`java.io.tmpdir`).

## 7. Configuração

| Propriedade | Padrão | Uso |
|---|---|---|
| `georural.lake.bucket.bruta` | — | Bucket da zona bruta. |
| `georural.lake.*` | — | Região, namespace, arquivo de configuração e perfil da OCI. |
| `georural.recebimento.prazo-abandono-minutos` | 15 | Prazo para considerar abandonado um registro `RECEBENDO`. |
| `spring.servlet.multipart.max-file-size` | 300MB | Tamanho máximo do upload. |

Credenciais da OCI (arquivo `config` e chave privada) e wallet do banco ficam fora do repositório.

## 8. Testes e pendências

**Testes automatizados** (unitários, com repositórios e armazenamento simulados): hash, validador de
formato, validador de conjunto, verificador de duplicidade, política e acesso à zona bruta, serviço de
recebimento (incluindo falha parcial em cada passo e retomada) e controller.

- Teste de integração contra o datalake: escrito (`ArmazenamentoZonaBrutaIntegracaoTest`), **ainda não executado**, aguardando a chave de API da OCI. Cada execução grava um objeto permanente.
- Envio ponta a ponta pelo endpoint (gravação no bucket e registro no Oracle).
- Unicidade real de `uk_arquivo_hash` e `409` em envios simultâneos (só verificável contra o Oracle).
- Regra de retenção do bucket e permissões de IAM do usuário da aplicação (seção 4).
- Espaço em disco do servidor para arquivos temporários de até 300 MB com envios simultâneos.
- Normalização dos nomes dos metadados (`hash-sha256`, `con-id`, `usu-id`) pela OCI.
- Ao esvaziar o datalake de desenvolvimento, limpar também os registros de `ARQUIVO_BRUTO`.