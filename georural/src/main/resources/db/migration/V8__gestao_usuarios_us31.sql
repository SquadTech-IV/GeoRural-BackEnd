-- =====================================================================
-- GeoRural DataHub - V8 - Suporte a gestao de usuarios (US#31.02)
-- =====================================================================

-- 1. Garante o seed do primeiro admin (idempotente) e remove eventuais
--    registros residuais sem usu_keycloak_id antes de aplicar NOT NULL.
MERGE INTO USUARIO dst
USING (
    SELECT (SELECT per_id FROM PERFIL WHERE per_funcao = 'ADMIN') AS per_id,
           '11111111-1111-1111-1111-111111111111'                 AS usu_keycloak_id,
           'Administrador'                                        AS usu_nome,
           'admin@georural.invalid'                               AS usu_email
      FROM DUAL
) src
ON (LOWER(dst.usu_email) = LOWER(src.usu_email))
WHEN MATCHED THEN
    UPDATE SET dst.per_id          = src.per_id,
               dst.usu_keycloak_id = src.usu_keycloak_id,
               dst.usu_status      = 'S'
WHEN NOT MATCHED THEN
    INSERT (per_id, usu_keycloak_id, usu_nome, usu_email, usu_status)
    VALUES (src.per_id, src.usu_keycloak_id, src.usu_nome, src.usu_email, 'S');

COMMIT;

DECLARE
    v_admin_id NUMBER;
BEGIN
    SELECT usu_id INTO v_admin_id
      FROM USUARIO
     WHERE LOWER(usu_email) = 'admin@georural.invalid';

    UPDATE AUDITORIA         SET usu_id = v_admin_id WHERE usu_id IN (SELECT usu_id FROM USUARIO WHERE usu_keycloak_id IS NULL);
    UPDATE ARQUIVO_BRUTO     SET usu_id = v_admin_id WHERE usu_id IN (SELECT usu_id FROM USUARIO WHERE usu_keycloak_id IS NULL);
    UPDATE EXECUCAO_PIPELINE SET usu_id = v_admin_id WHERE usu_id IN (SELECT usu_id FROM USUARIO WHERE usu_keycloak_id IS NULL);
    UPDATE VERSAO            SET usu_id = v_admin_id WHERE usu_id IN (SELECT usu_id FROM USUARIO WHERE usu_keycloak_id IS NULL);

    DELETE FROM USUARIO
     WHERE usu_keycloak_id IS NULL;

    COMMIT;
END;
/

-- 2. Aplica usu_keycloak_id NOT NULL (idempotente)
DECLARE
    v_nullable VARCHAR2(1);
BEGIN
    SELECT nullable
      INTO v_nullable
      FROM user_tab_columns
     WHERE table_name = 'USUARIO'
       AND column_name = 'USU_KEYCLOAK_ID';

    IF v_nullable = 'Y' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE USUARIO MODIFY (usu_keycloak_id NOT NULL)';
    END IF;
END;
/

-- 3. Garante constraint UNIQUE em usu_keycloak_id e indice UNIQUE em LOWER(usu_email)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_cons_columns ucc
      JOIN user_constraints uc
        ON uc.constraint_name = ucc.constraint_name
     WHERE ucc.table_name = 'USUARIO'
       AND ucc.column_name = 'USU_KEYCLOAK_ID'
       AND uc.constraint_type IN ('U', 'P');

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE USUARIO ADD CONSTRAINT uk_usuario_keycloak UNIQUE (usu_keycloak_id)';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'USUARIO'
       AND index_name = 'UK_USUARIO_EMAIL_LOWER';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uk_usuario_email_lower ON USUARIO (LOWER(usu_email))';
    END IF;
END;
/

-- 4. Revoga UPDATE e DELETE sobre AUDITORIA ao usuario da aplicacao
--    (ignora se a sessao conectada for o proprio owner da tabela, onde REVOKE nao se aplica)
DECLARE
    v_owner VARCHAR2(128);
    v_user  VARCHAR2(128);
BEGIN
    SELECT owner INTO v_owner
      FROM all_tables
     WHERE table_name = 'AUDITORIA'
       AND ROWNUM = 1;

    v_user := SYS_CONTEXT('USERENV', 'SESSION_USER');

    IF v_owner <> v_user THEN
        EXECUTE IMMEDIATE 'REVOKE UPDATE, DELETE ON ' || v_owner || '.AUDITORIA FROM ' || v_user;
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        -- ORA-01927: privilegio nao concedido diretamente (ja nao possui UPDATE/DELETE)
        IF SQLCODE <> -1927 THEN
            RAISE;
        END IF;
END;
/