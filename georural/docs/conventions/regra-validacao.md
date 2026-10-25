# Catálogo de Regras de Validação (US#08)

| Código | Nome | Descrição | Parâmetros Exemplo | Mensagem de Motivo |
|---|---|---|---|---|
| **VAL-001** | Campos Obrigatórios | Valida se todos os atributos obrigatórios definidos no esquema estão preenchidos. | `campo` | Campo obrigatório ausente ou nulo: {campo} |
| **VAL-002** | Tipos de Dados | Verifica a conformidade dos tipos de dados de cada campo em relação ao esquema. | `campo`, `tipoEsperado` | Valor do campo '{campo}' incompatível com o tipo '{tipoEsperado}' |
| **VAL-003** | Domínio de Valores | Garante que o valor preenchido pertence à lista/enum de valores válidos. | `campo`, `valor` | Valor '{valor}' fora do domínio permitido para o campo '{campo}' |
| **VAL-004** | Duplicidade | Checa registros idênticos dentro do arquivo importado. | `chave` | Registro duplicado detectado com a chave/hash: {chave} |
| **VAL-005** | Áreas | Verifica a consistência dos limites e valores de área declarados. | `area` | Valor de área inválido ou fora dos limites permitidos: {area} |
| **VAL-006** | Coordenadas | Garante que as coordenadas do registro estão contidas na projeção/extensão válida. | N/A | Coordenadas geográficas inválidas ou fora dos limites aceitos |
| **VAL-007** | Geometrias | Valida a integridade espacial; reprova geometrias que falharem no reparo automático. | N/A | Geometria inválida e não passível de reparo autônomo |