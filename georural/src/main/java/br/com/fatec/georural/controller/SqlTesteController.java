package br.com.fatec.georural.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import lombok.RequiredArgsConstructor;

/**
 * Consultas de conferência (SÓ PARA TESTE; remova antes de ir para produção).
 *
 * Todas as consultas devolvem uma lista de objetos {COLUNA: valor}, bem mais
 * fácil de ler no Postman do que arrays soltos.
 *
 * Atalho: GET /api/teste/checklist roda todas as verificações das alterações
 * (SRID, validação fechada, quarentena, transformações, hash) e devolve
 * OK / FALHA / PENDENTE para cada uma.
 */
@RestController
@RequestMapping("/api/teste")
@RequiredArgsConstructor
public class SqlTesteController {

    private static final String BASE_ZONA_TRATADA = "./zona-tratada";

    private final EntityManager entityManager;

    // =========================================================================
    // Execução, transformações, versão
    // =========================================================================

    @GetMapping("/execucao")
    public Object execucao() {
        return consulta("""
                SELECT * FROM EXECUCAO_PIPELINE
                ORDER BY EXE_ID DESC
                FETCH FIRST 5 ROWS ONLY
                """);
    }

    /**
     * Esperado: TRA_ORDEM sequencial (1,2,3...) sem repetição na mesma EXE_ID.
     * Sem parâmetro, mostra a última execução.
     */
    @GetMapping("/transformacao")
    public Object transformacao(@RequestParam(required = false) Long exeId) {

        if (exeId != null) {
            return consulta("""
                    SELECT TRA_ID, EXE_ID, TRA_ORDEM, TRA_TIPO, TRA_DESCRICAO
                    FROM TRANSFORMACAO
                    WHERE EXE_ID = ?1
                    ORDER BY TRA_ORDEM
                    """, exeId);
        }

        return consulta("""
                SELECT TRA_ID, EXE_ID, TRA_ORDEM, TRA_TIPO, TRA_DESCRICAO
                FROM TRANSFORMACAO
                WHERE EXE_ID = (SELECT MAX(EXE_ID) FROM TRANSFORMACAO)
                ORDER BY TRA_ORDEM
                """);
    }

    /** Esperado após sucesso: VER_SITUACAO = VIGENTE e VER_EXE_ID preenchido. */
    @GetMapping("/versao")
    public Object versao() {
        return consulta("""
                SELECT VER_ID, CON_ID, VER_NUMERO, VER_SITUACAO, VER_EXE_ID, VER_HASH
                FROM VERSAO
                ORDER BY VER_ID DESC
                FETCH FIRST 10 ROWS ONLY
                """);
    }

    // =========================================================================
    // Validação e quarentena (item 3)
    // =========================================================================

    /**
     * Esperado: EXE_ID preenchido, totais preenchidos,
     * VALIDOS + INVALIDOS = TOTAL, status REJEITADO se INVALIDOS > 0,
     * e QTD_QUARENTENA igual a VAL_REGISTROS_INVALIDOS.
     */
    @GetMapping("/validacao")
    public Object validacao() {
        return consulta("""
                SELECT v.VAL_ID, v.EXE_ID, v.ARQ_BRUTO_ID,
                       v.VAL_TOTAL_REGISTROS, v.VAL_REGISTROS_VALIDOS,
                       v.VAL_REGISTROS_INVALIDOS, v.VAL_STATUS_FINAL,
                       v.VAL_DATA_EXECUCAO,
                       (SELECT COUNT(*) FROM QUARENTENA q WHERE q.VAL_ID = v.VAL_ID)
                           AS QTD_QUARENTENA
                FROM VALIDACAO v
                ORDER BY v.VAL_ID DESC
                FETCH FIRST 5 ROWS ONLY
                """);
    }

    @GetMapping("/quarentena")
    public Object quarentena(@RequestParam(required = false) Long valId) {

        if (valId != null) {
            return consulta("""
                    SELECT QUA_ID, VAL_ID, QUA_LINHA_ORIGEM, QUA_COLUNA_ERRO,
                           QUA_MOTIVO_ERRO, QUA_SITUACAO, QUA_DATA_REGISTRO,
                           JSON_SERIALIZE(QUA_DADO_ORIGINAL RETURNING VARCHAR2(1000) TRUNCATE)
                               AS DADO_ORIGINAL
                    FROM QUARENTENA
                    WHERE VAL_ID = ?1
                    ORDER BY QUA_ID
                    FETCH FIRST 100 ROWS ONLY
                    """, valId);
        }

        return consulta("""
                SELECT QUA_ID, VAL_ID, QUA_LINHA_ORIGEM, QUA_COLUNA_ERRO,
                       QUA_MOTIVO_ERRO, QUA_SITUACAO, QUA_DATA_REGISTRO,
                       JSON_SERIALIZE(QUA_DADO_ORIGINAL RETURNING VARCHAR2(1000) TRUNCATE)
                           AS DADO_ORIGINAL
                FROM QUARENTENA
                ORDER BY QUA_ID DESC
                FETCH FIRST 50 ROWS ONLY
                """);
    }

    @GetMapping("/quarentena/total")
    public Object quarentenaTotal() {
        return entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM QUARENTENA").getSingleResult();
    }

    // =========================================================================
    // Movimentação de zona (corrigido: a coluna é MOV_OBS)
    // =========================================================================

    @GetMapping("/movimentacao")
    public Object movimentacao() {
        return consulta("""
                SELECT MOV_ID, ARQ_BRUTO_ID, EXE_ID, MOV_ZONA, MOV_STATUS,
                       MOV_DATA, MOV_OBS
                FROM MOVIMENTACAO_ZONA
                ORDER BY MOV_ID DESC
                FETCH FIRST 10 ROWS ONLY
                """);
    }

    // =========================================================================
    // Imóveis: SRID e unicidade (correção do ORA-13199 e do ORA-00001)
    // =========================================================================

    /** Esperado: uma única linha, SRID = 4674. Qualquer NULL indica o bug antigo. */
    @GetMapping("/imovel/srid")
    public Object imovelSrid() {
        return consulta("""
                SELECT NVL(TO_CHAR(i.IMO_GEOMETRIA.SDO_SRID), 'NULL') AS SRID,
                       COUNT(*) AS TOTAL
                FROM IMOVEL_RURAL i
                GROUP BY i.IMO_GEOMETRIA.SDO_SRID
                """);
    }

    /** Esperado: lista vazia (UK_IMOVEL_CAR garante um registro por CAR). */
    @GetMapping("/imovel/duplicados")
    public Object imovelDuplicados() {
        return consulta("""
                SELECT IMO_CODIGO_CAR, COUNT(*) AS TOTAL
                FROM IMOVEL_RURAL
                GROUP BY IMO_CODIGO_CAR
                HAVING COUNT(*) > 1
                """);
    }

    /**
     * Amostra de imóveis. Ao reprocessar, o IMO_ID deve continuar o mesmo e o
     * VER_ID deve acompanhar a versão mais nova (upsert).
     */
    @GetMapping("/imovel")
    public Object imovel() {
        return consulta("""
                SELECT i.IMO_ID, i.IMO_CODIGO_CAR, i.VER_ID, i.ARQ_BRUTO_ID,
                       i.IMO_AREA_TOTAL, i.MUN_ID,
                       i.IMO_GEOMETRIA.SDO_SRID AS SRID
                FROM IMOVEL_RURAL i
                ORDER BY i.IMO_ID DESC
                FETCH FIRST 20 ROWS ONLY
                """);
    }

    // =========================================================================
    // Hash da saída tratada (US09.06)
    // =========================================================================

    /**
     * Últimos hashes gravados na zona tratada de imóveis. Processando o mesmo
     * arquivo duas vezes, os dois hashes mais recentes devem ser IGUAIS
     * (com EXE_ID e caminho diferentes).
     */
    @GetMapping("/hash-saida")
    public Object hashSaida() {

        List<Map<String, Object>> linhas = hashesSaida(5);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("ultimasGravacoes", linhas);

        if (linhas.size() >= 2) {
            resposta.put("doisUltimosIguais",
                    String.valueOf(linhas.get(0).get("HASH_SAIDA"))
                            .equals(String.valueOf(linhas.get(1).get("HASH_SAIDA"))));
        } else {
            resposta.put("doisUltimosIguais",
                    "PENDENTE: processe o mesmo arquivo pelo menos duas vezes");
        }

        return resposta;
    }

    // =========================================================================
    // Constraints e zona tratada em disco
    // =========================================================================

    /** Lista as check constraints das tabelas do pipeline (valores permitidos). */
    @GetMapping("/constraints")
    public Object constraints() {
        return consulta("""
                SELECT TABLE_NAME, CONSTRAINT_NAME, SEARCH_CONDITION_VC
                FROM USER_CONSTRAINTS
                WHERE CONSTRAINT_TYPE = 'C'
                  AND TABLE_NAME IN ('TRANSFORMACAO','QUARENTENA','VALIDACAO',
                                     'MOVIMENTACAO_ZONA','EXECUCAO_PIPELINE','VERSAO')
                ORDER BY TABLE_NAME, CONSTRAINT_NAME
                """);
    }

    /** Lista os arquivos gravados na zona tratada (modo local). */
    @GetMapping("/zona-tratada")
    public List<String> zonaTratada() throws IOException {
        Path base = Path.of(BASE_ZONA_TRATADA);

        if (!Files.exists(base)) {
            return List.of("(pasta ./zona-tratada não existe: nada foi gravado "
                    + "em disco; se o storage é oci, veja o bucket na OCI)");
        }

        try (Stream<Path> arquivos = Files.walk(base)) {
            return arquivos
                    .filter(Files::isRegularFile)
                    .map(p -> p + "  (" + p.toFile().length() + " bytes)")
                    .toList();
        }
    }

    /**
     * Confere um arquivo da zona tratada: mostra as 2 primeiras linhas, o
     * número de linhas, o SHA-256 calculado e se as linhas estão ordenadas
     * por recibo. Compare o SHA-256 com o "sha256=" de MOVIMENTACAO_ZONA.
     *
     * Exemplo: /api/teste/zona-tratada/conteudo?caminho=./zona-tratada/tratada/imovel/v1/exec-5/saida.jsonl
     */
    @GetMapping("/zona-tratada/conteudo")
    public ResponseEntity<Object> zonaTratadaConteudo(@RequestParam String caminho)
            throws Exception {

        if (caminho.startsWith("oci://")) {
            return ResponseEntity.badRequest().body(
                    "Caminho OCI: confira o objeto pelo Console da OCI. "
                            + "Esta consulta lê apenas o modo local.");
        }

        Path base = Path.of(BASE_ZONA_TRATADA).toAbsolutePath().normalize();
        Path arquivo = Path.of(caminho).toAbsolutePath().normalize();

        // só permite ler dentro de ./zona-tratada
        if (!arquivo.startsWith(base) || !Files.isRegularFile(arquivo)) {
            return ResponseEntity.badRequest().body(
                    "Arquivo não encontrado dentro de " + BASE_ZONA_TRATADA);
        }

        byte[] bytes = Files.readAllBytes(arquivo);

        String sha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));

        List<String> linhas = new String(bytes, StandardCharsets.UTF_8)
                .lines()
                .toList();

        List<String> recibos = new ArrayList<>();

        for (String linha : linhas) {
            recibos.add(extrairRecibo(linha));
        }

        boolean ordenado = true;

        for (int i = 1; i < recibos.size(); i++) {
            if (recibos.get(i - 1).compareTo(recibos.get(i)) > 0) {
                ordenado = false;
                break;
            }
        }

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("arquivo", arquivo.toString());
        resposta.put("bytes", bytes.length);
        resposta.put("linhas", linhas.size());
        resposta.put("sha256", sha256);
        resposta.put("ordenadoPorRecibo", ordenado);
        resposta.put("primeirasLinhas",
                linhas.stream().limit(2).map(this::resumir).toList());

        return ResponseEntity.ok(resposta);
    }

    // =========================================================================
    // Checklist geral
    // =========================================================================

    /**
     * Roda todas as verificações das alterações e devolve OK / FALHA / PENDENTE.
     * Rode depois de processar o Area_do_Imovel.shp (de preferência duas vezes).
     */
    @GetMapping("/checklist")
    public Object checklist() {

        Map<String, Object> resultado = new LinkedHashMap<>();

        // 1) SRID dos imóveis
        long totalImoveis = numero(
                "SELECT COUNT(*) FROM IMOVEL_RURAL");

        long semSrid = numero("""
                SELECT COUNT(*) FROM IMOVEL_RURAL i
                WHERE i.IMO_GEOMETRIA.SDO_SRID IS NULL
                   OR i.IMO_GEOMETRIA.SDO_SRID <> 4674
                """);

        resultado.put("1_srid_4674", totalImoveis == 0
                ? item("PENDENTE", "nenhum imóvel gravado ainda")
                : item(semSrid == 0 ? "OK" : "FALHA",
                        totalImoveis + " imóveis, " + semSrid + " com SRID diferente de 4674"));

        // 2) Um registro por CAR
        long duplicados = numero("""
                SELECT COUNT(*) FROM (
                    SELECT IMO_CODIGO_CAR FROM IMOVEL_RURAL
                    GROUP BY IMO_CODIGO_CAR HAVING COUNT(*) > 1)
                """);

        resultado.put("2_sem_car_duplicado", item(
                duplicados == 0 ? "OK" : "FALHA",
                duplicados + " CARs duplicados"));

        // Última execução de tratamento concluída
        List<Map<String, Object>> exec = consulta("""
                SELECT EXE_ID FROM EXECUCAO_PIPELINE
                WHERE EXE_STATUS = 'CONCLUIDA'
                ORDER BY EXE_ID DESC
                FETCH FIRST 1 ROWS ONLY
                """);

        if (exec.isEmpty()) {
            resultado.put("3_execucao", item("PENDENTE",
                    "nenhuma execução CONCLUIDA; reenvie o arquivo"));
            return resultado;
        }

        long exeId = ((Number) exec.get(0).get("EXE_ID")).longValue();

        resultado.put("3_execucao", item("OK", "última execução concluída: exe_id=" + exeId));

        // 4) Validação fechada
        List<Map<String, Object>> val = consulta("""
                SELECT v.VAL_ID, v.EXE_ID, v.VAL_TOTAL_REGISTROS,
                       v.VAL_REGISTROS_VALIDOS, v.VAL_REGISTROS_INVALIDOS,
                       v.VAL_STATUS_FINAL,
                       (SELECT COUNT(*) FROM QUARENTENA q WHERE q.VAL_ID = v.VAL_ID)
                           AS QTD_QUARENTENA
                FROM VALIDACAO v
                WHERE v.EXE_ID = ?1
                ORDER BY v.VAL_ID DESC
                FETCH FIRST 1 ROWS ONLY
                """, exeId);

        if (val.isEmpty()) {
            resultado.put("4_validacao_fechada", item("FALHA",
                    "nenhuma VALIDACAO ligada à execução (exe_id não preenchido)"));
            resultado.put("5_quarentena_bate", item("PENDENTE", "sem validação"));
        } else {

            Map<String, Object> v = val.get(0);

            Long total = longOuNulo(v.get("VAL_TOTAL_REGISTROS"));
            Long validos = longOuNulo(v.get("VAL_REGISTROS_VALIDOS"));
            Long invalidos = longOuNulo(v.get("VAL_REGISTROS_INVALIDOS"));
            String status = (String) v.get("VAL_STATUS_FINAL");
            long quarentena = ((Number) v.get("QTD_QUARENTENA")).longValue();

            boolean preenchido = total != null && validos != null && invalidos != null;

            boolean somaOk = preenchido && validos + invalidos == total;

            boolean statusOk = preenchido
                    && (invalidos > 0 ? "REJEITADO" : "APROVADO").equals(status);

            resultado.put("4_validacao_fechada", item(
                    preenchido && somaOk && statusOk ? "OK" : "FALHA",
                    "total=" + total + ", validos=" + validos
                            + ", invalidos=" + invalidos + ", status=" + status));

            resultado.put("5_quarentena_bate", item(
                    preenchido && quarentena == invalidos ? "OK" : "FALHA",
                    "linhas na QUARENTENA=" + quarentena + ", invalidos=" + invalidos));
        }

        // 6) Transformações sequenciais e completas
        List<Map<String, Object>> tra = consulta("""
                SELECT COUNT(*) AS QTD, MIN(TRA_ORDEM) AS MENOR,
                       MAX(TRA_ORDEM) AS MAIOR,
                       COUNT(DISTINCT TRA_ORDEM) AS DISTINTAS
                FROM TRANSFORMACAO
                WHERE EXE_ID = ?1
                """, exeId);

        Map<String, Object> t = tra.get(0);

        long qtd = ((Number) t.get("QTD")).longValue();
        long menor = t.get("MENOR") == null ? 0 : ((Number) t.get("MENOR")).longValue();
        long maior = t.get("MAIOR") == null ? 0 : ((Number) t.get("MAIOR")).longValue();
        long distintas = ((Number) t.get("DISTINTAS")).longValue();

        boolean sequencial = qtd > 0 && menor == 1 && maior == qtd && distintas == qtd;

        List<String> esperadas = List.of("CARGA", "PADRONIZACAO", "REPROJECAO",
                "RECORTE", "DISSOLVER", "CALCULO_AREA", "GRAVACAO_ZONA_TRATADA");

        List<String> faltando = new ArrayList<>();

        for (String nome : esperadas) {
            long achou = numero("""
                    SELECT COUNT(*) FROM TRANSFORMACAO
                    WHERE EXE_ID = ?1 AND TRA_DESCRICAO LIKE ?2
                    """, exeId, "[" + nome + "]%");

            if (achou == 0) {
                faltando.add(nome);
            }
        }

        resultado.put("6_transformacoes", item(
                sequencial && faltando.isEmpty() ? "OK" : "FALHA",
                qtd + " transformações, ordem 1.." + maior
                        + (faltando.isEmpty() ? "" : ", faltando: " + faltando)));

        // 7) Versão ligada à execução
        long versaoLigada = numero("""
                SELECT COUNT(*) FROM VERSAO
                WHERE VER_EXE_ID = ?1 AND VER_SITUACAO = 'VIGENTE'
                """, exeId);

        resultado.put("7_versao_vigente_ligada", item(
                versaoLigada == 1 ? "OK" : "FALHA",
                versaoLigada + " versão(ões) VIGENTE ligada(s) à execução"));

        // 8) Movimentação TRATADA com caminho e hash
        long movimentacao = numero("""
                SELECT COUNT(*) FROM MOVIMENTACAO_ZONA
                WHERE EXE_ID = ?1 AND MOV_ZONA = 'TRATADA'
                  AND MOV_OBS LIKE '%caminho=%' AND MOV_OBS LIKE '%sha256=%'
                """, exeId);

        resultado.put("8_movimentacao_tratada", item(
                movimentacao >= 1 ? "OK" : "FALHA",
                movimentacao + " movimentação(ões) TRATADA com caminho e sha256"));

        // 9) Determinismo do hash (US09.06)
        List<Map<String, Object>> hashes = hashesSaida(2);

        if (hashes.size() < 2) {
            resultado.put("9_hash_reprocessamento", item("PENDENTE",
                    "processe o mesmo arquivo pelo menos duas vezes"));
        } else {

            String h1 = String.valueOf(hashes.get(0).get("HASH_SAIDA"));
            String h2 = String.valueOf(hashes.get(1).get("HASH_SAIDA"));

            resultado.put("9_hash_reprocessamento", item(
                    h1.equals(h2) ? "OK" : "FALHA",
                    "exec " + hashes.get(0).get("EXE_ID") + "=" + abreviar(h1)
                            + " | exec " + hashes.get(1).get("EXE_ID") + "=" + abreviar(h2)));
        }

        return resultado;
    }

    // =========================================================================
    // Apoio
    // =========================================================================

    /** Hashes gravados na zona tratada de IMÓVEIS, do mais recente para o mais antigo. */
    private List<Map<String, Object>> hashesSaida(int limite) {
        return consulta("""
                SELECT EXE_ID,
                       REGEXP_SUBSTR(TRA_DESCRICAO, 'sha256=([0-9a-f]{64})', 1, 1, NULL, 1)
                           AS HASH_SAIDA,
                       REGEXP_SUBSTR(TRA_DESCRICAO, 'caminho=([^,]+)', 1, 1, NULL, 1)
                           AS CAMINHO
                FROM TRANSFORMACAO
                WHERE TRA_DESCRICAO LIKE '[GRAVACAO_ZONA_TRATADA]%'
                  AND TRA_DESCRICAO LIKE '%imovel%'
                ORDER BY EXE_ID DESC
                FETCH FIRST ?1 ROWS ONLY
                """, limite);
    }

    /** Executa o SELECT e devolve cada linha como {COLUNA: valor}. */
    private List<Map<String, Object>> consulta(String sql, Object... parametros) {

        Query query = entityManager.createNativeQuery(sql, Tuple.class);

        for (int i = 0; i < parametros.length; i++) {
            query.setParameter(i + 1, parametros[i]);
        }

        List<Map<String, Object>> linhas = new ArrayList<>();

        for (Object o : query.getResultList()) {

            Tuple tuple = (Tuple) o;

            Map<String, Object> linha = new LinkedHashMap<>();

            for (TupleElement<?> coluna : tuple.getElements()) {
                linha.put(coluna.getAlias(), tuple.get(coluna));
            }

            linhas.add(linha);
        }

        return linhas;
    }

    private long numero(String sql, Object... parametros) {

        Query query = entityManager.createNativeQuery(sql);

        for (int i = 0; i < parametros.length; i++) {
            query.setParameter(i + 1, parametros[i]);
        }

        Object valor = query.getSingleResult();

        return valor == null ? 0 : ((Number) valor).longValue();
    }

    private Map<String, Object> item(String status, String detalhe) {

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("status", status);
        item.put("detalhe", detalhe);

        return item;
    }

    private Long longOuNulo(Object valor) {
        return valor == null ? null : ((Number) valor).longValue();
    }

    private String abreviar(String hash) {
        return hash.length() > 12 ? hash.substring(0, 12) + "..." : hash;
    }

    /** Extrai o valor de "recibo" de uma linha JSON sem depender de biblioteca. */
    private String extrairRecibo(String linhaJson) {

        String chave = "\"recibo\":\"";

        int inicio = linhaJson.indexOf(chave);

        if (inicio < 0) {
            return "";
        }

        inicio += chave.length();

        int fim = linhaJson.indexOf('"', inicio);

        return fim < 0 ? "" : linhaJson.substring(inicio, fim);
    }

    /** Evita devolver o WKT inteiro no Postman: corta linhas muito longas. */
    private String resumir(String linha) {
        return linha.length() > 400 ? linha.substring(0, 400) + "..." : linha;
    }
}