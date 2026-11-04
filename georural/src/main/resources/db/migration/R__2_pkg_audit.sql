-- =====================================================================
-- GeoRural DataHub - PKG_AUDIT
-- =====================================================================
-- COMO O BANCO DESCOBRE QUEM E O USUARIO LOGADO NO KEYCLOAK

CREATE OR REPLACE PACKAGE PKG_AUDIT AS
    FUNCTION usuario_atual_id   RETURN NUMBER;
    FUNCTION usuario_atual_nome RETURN VARCHAR2;

    PROCEDURE registrar(
        p_operacao      VARCHAR2,
        p_entidade      VARCHAR2,
        p_registro_id   NUMBER,
        p_dados_novos   CLOB DEFAULT NULL,
        p_dados_antigos CLOB DEFAULT NULL);
END PKG_AUDIT;
/

CREATE OR REPLACE PACKAGE BODY PKG_AUDIT AS

    -- Resolve o usuario local a partir do identificador da sessao.
    -- Tenta primeiro pelo 'sub' do Keycloak; se nao achar, cai para o e-mail
    -- (util enquanto os usuarios do seed ainda nao fizeram o primeiro login).
    FUNCTION usuario_atual_id RETURN NUMBER IS
        v_ident VARCHAR2(64) := SYS_CONTEXT('USERENV','CLIENT_IDENTIFIER');
        v_id    NUMBER;
    BEGIN
        IF v_ident IS NULL THEN
            RETURN NULL;
        END IF;

        BEGIN
            SELECT usu_id INTO v_id
              FROM USUARIO
             WHERE usu_keycloak_id = v_ident;
            RETURN v_id;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN NULL;
        END;

        BEGIN
            SELECT usu_id INTO v_id
              FROM USUARIO
             WHERE LOWER(usu_email) = LOWER(v_ident);
            RETURN v_id;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN RETURN NULL;
            WHEN TOO_MANY_ROWS  THEN RETURN NULL;
        END;
    END usuario_atual_id;

    -- Nome para o retrato em AUDITORIA. Se o usuario nao estiver espelhado ainda,
    -- devolve o proprio identificador, para a trilha nunca ficar sem autor.
    FUNCTION usuario_atual_nome RETURN VARCHAR2 IS
        v_id   NUMBER := usuario_atual_id;
        v_nome VARCHAR2(120);
    BEGIN
        IF v_id IS NULL THEN
            RETURN SUBSTR(SYS_CONTEXT('USERENV','CLIENT_IDENTIFIER'), 1, 120);
        END IF;
        SELECT usu_nome INTO v_nome FROM USUARIO WHERE usu_id = v_id;
        RETURN v_nome;
    EXCEPTION
        WHEN OTHERS THEN RETURN NULL;
    END usuario_atual_nome;

    -- Grava a trilha. AUTONOMOUS_TRANSACTION de proposito: a auditoria do evento
    -- permanece mesmo que a transacao principal sofra rollback.
    PROCEDURE registrar(
        p_operacao      VARCHAR2,
        p_entidade      VARCHAR2,
        p_registro_id   NUMBER,
        p_dados_novos   CLOB DEFAULT NULL,
        p_dados_antigos CLOB DEFAULT NULL)
    IS
        PRAGMA AUTONOMOUS_TRANSACTION;
    BEGIN
        INSERT INTO AUDITORIA (
            usu_id, aud_usuario_nome, aud_operacao, aud_entidade_afetada,
            aud_registro_id, aud_dados_novos, aud_dados_antigos)
        VALUES (
            usuario_atual_id, usuario_atual_nome, p_operacao, p_entidade,
            p_registro_id, p_dados_novos, p_dados_antigos);
        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK;
            -- auditoria nunca derruba a operacao de negocio
            NULL;
    END registrar;

END PKG_AUDIT;
/
