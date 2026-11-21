-- =====================================================================
-- GeoRural DataHub - V6 - Ajuste de USUARIO/PERFIL e vinculo Keycloak (US#13.03)
-- =====================================================================

-- 1. Garante os 5 perfis oficiais do sistema (idempotente)
MERGE INTO PERFIL dst
USING (
    SELECT 'ADMIN'    AS per_funcao, 'Administrador'     AS per_nome FROM DUAL UNION ALL
    SELECT 'OPERADOR' AS per_funcao, 'Operador de dados' AS per_nome FROM DUAL UNION ALL
    SELECT 'ANALISTA' AS per_funcao, 'Analista'          AS per_nome FROM DUAL UNION ALL
    SELECT 'GESTOR'   AS per_funcao, 'Gestor'            AS per_nome FROM DUAL UNION ALL
    SELECT 'AUDITOR'  AS per_funcao, 'Auditor'           AS per_nome FROM DUAL
) src
ON (dst.per_funcao = src.per_funcao)
WHEN MATCHED THEN
    UPDATE SET dst.per_nome = src.per_nome
WHEN NOT MATCHED THEN
    INSERT (per_funcao, per_nome) VALUES (src.per_funcao, src.per_nome);

COMMIT;

-- 2. Garante a FK usuario.per_id -> perfil.per_id
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_constraints
     WHERE table_name = 'USUARIO'
       AND constraint_type = 'R'
       AND r_constraint_name = (
           SELECT constraint_name
             FROM user_constraints
            WHERE table_name = 'PERFIL'
              AND constraint_type = 'P'
       );

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE USUARIO ADD CONSTRAINT fk_usu_perfil FOREIGN KEY (per_id) REFERENCES PERFIL (per_id)';
    END IF;
END;
/

-- 3. Vincula os usuarios espelho aos UUIDs fixos do Keycloak (US#13.02)
--    usu_keycloak_id permanece nulavel (NOT NULL fica para a US#31.02)
MERGE INTO USUARIO dst
USING (
    SELECT (SELECT per_id FROM PERFIL WHERE per_funcao = 'ADMIN')    AS per_id,
           '11111111-1111-1111-1111-111111111111'                    AS usu_keycloak_id,
           'Administrador'                                           AS usu_nome,
           'admin@georural.invalid'                                  AS usu_email
      FROM DUAL
    UNION ALL
    SELECT (SELECT per_id FROM PERFIL WHERE per_funcao = 'GESTOR'),
           '22222222-2222-2222-2222-222222222222',
           'Gestor',
           'gestor@georural.invalid'
      FROM DUAL
    UNION ALL
    SELECT (SELECT per_id FROM PERFIL WHERE per_funcao = 'OPERADOR'),
           '33333333-3333-3333-3333-333333333333',
           'Operador Um',
           'operador@georural.invalid'
      FROM DUAL
    UNION ALL
    SELECT (SELECT per_id FROM PERFIL WHERE per_funcao = 'OPERADOR'),
           '66666666-6666-6666-6666-666666666666',
           'Operador Dois',
           'operador2@georural.invalid'
      FROM DUAL
    UNION ALL
    SELECT (SELECT per_id FROM PERFIL WHERE per_funcao = 'ANALISTA'),
           '44444444-4444-4444-4444-444444444444',
           'Analista',
           'analista@georural.invalid'
      FROM DUAL
    UNION ALL
    SELECT (SELECT per_id FROM PERFIL WHERE per_funcao = 'AUDITOR'),
           '55555555-5555-5555-5555-555555555555',
           'Auditor',
           'auditor@georural.invalid'
      FROM DUAL
) src
ON (LOWER(dst.usu_email) = LOWER(src.usu_email))
WHEN MATCHED THEN
    UPDATE SET dst.per_id          = src.per_id,
               dst.usu_keycloak_id = src.usu_keycloak_id,
               dst.usu_nome        = src.usu_nome,
               dst.usu_status      = 'S'
WHEN NOT MATCHED THEN
    INSERT (per_id, usu_keycloak_id, usu_nome, usu_email, usu_status)
    VALUES (src.per_id, src.usu_keycloak_id, src.usu_nome, src.usu_email, 'S');

COMMIT;

-- 4. Limpa usuarios de teste avulsos (preservando os espelhos oficiais @georural.invalid)
DECLARE
    v_admin_id NUMBER;
BEGIN
    SELECT usu_id INTO v_admin_id
      FROM USUARIO
     WHERE LOWER(usu_email) = 'admin@georural.invalid';

    UPDATE AUDITORIA         SET usu_id = v_admin_id WHERE usu_id IN (SELECT usu_id FROM USUARIO WHERE LOWER(usu_email) NOT LIKE '%@georural.invalid');
    UPDATE ARQUIVO_BRUTO     SET usu_id = v_admin_id WHERE usu_id IN (SELECT usu_id FROM USUARIO WHERE LOWER(usu_email) NOT LIKE '%@georural.invalid');
    UPDATE EXECUCAO_PIPELINE SET usu_id = v_admin_id WHERE usu_id IN (SELECT usu_id FROM USUARIO WHERE LOWER(usu_email) NOT LIKE '%@georural.invalid');
    UPDATE VERSAO            SET usu_id = v_admin_id WHERE usu_id IN (SELECT usu_id FROM USUARIO WHERE LOWER(usu_email) NOT LIKE '%@georural.invalid');

    DELETE FROM USUARIO
     WHERE LOWER(usu_email) NOT LIKE '%@georural.invalid';

    COMMIT;
END;
/